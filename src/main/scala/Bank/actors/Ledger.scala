package Bank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import Bank.models.*
import scala.concurrent.duration.*

object Ledger:

  def apply(output: String => Unit = println): Behavior[LedgerMessage] =
    Behaviors.setup { context =>
      recording(context, Vector.empty, Map.empty, output)
    }

  private def recording(
                         context:  ActorContext[LedgerMessage],
                         history:  Vector[String],
                         accounts: Map[String, ActorRef[BankMessage]],
                         output:   String => Unit
                       ): Behavior[LedgerMessage] =
    Behaviors.receiveMessage:

      case RegisterAccount(id, ref) =>
        recording(context, history, accounts + (id -> ref), output)

      case LogEvent(message) =>
        output(message)
        recording(context, history :+ message, accounts, output)

      case LogDebit(account, amount, newBalance) =>
        val entry = s"DEBIT    | $account | montant=$amount | nouveau solde=$newBalance"
        output(entry)
        recording(context, history :+ entry, accounts, output)

      case LogCredit(account, amount, newBalance) =>
        val entry = s"CREDIT   | $account | montant=$amount | nouveau solde=$newBalance"
        output(entry)
        recording(context, history :+ entry, accounts, output)

      // ── Après confirmation : affiche les soldes des deux comptes concernés ─
      case LogTransfer(from, to, amount, Success) =>
        val entry = s"CONFIRME | $from -> $to | montant=$amount"
        output(entry)
        val involved = Vector(from, to).filter(accounts.contains)
        if involved.nonEmpty then
          val collector = context.spawnAnonymous(
            collectBalancesOrdered(involved.toSet, Map.empty, involved, output)
          )
          involved.foreach(id => accounts(id) ! GetBalance(collector))
        recording(context, history :+ entry, accounts, output)

      // ── Échec : pas de solde à afficher, rien n'a changé ──────────────────
      case LogTransfer(from, to, amount, Failure) =>
        val entry = s"ECHEC    | $from -> $to | montant=$amount"
        output(entry)
        recording(context, history :+ entry, accounts, output)

      // ── Solde d'un seul compte à la demande ───────────────────────────────
      case QueryBalance(accountId) =>
        accounts.get(accountId) match
          case Some(ref) =>
            val collector = context.spawnAnonymous(
              Behaviors.receiveMessage[BalanceResponse]:
                case BalanceResult(id, balance) =>
                  output(s"BALANCE  | $id | solde=$balance EUR")
                  Behaviors.stopped
                case _ =>
                  Behaviors.same
            )
            ref ! GetBalance(collector)
          case None =>
            output(s"[WARN] compte inconnu : $accountId")
        recording(context, history, accounts, output)

      // ── Tous les soldes dans l'ordre de création ──────────────────────────
      case PrintBalancesOrdered(order) =>
        if accounts.isEmpty then
          output("(aucun compte enregistré)")
          Behaviors.same
        else
          val toQuery = order.filter(accounts.contains)
          val collector = context.spawnAnonymous(
            collectBalancesOrdered(toQuery.toSet, Map.empty, toQuery, output)
          )
          toQuery.foreach(id => accounts(id) ! GetBalance(collector))
          Behaviors.same

      case PrintHistory =>
        output("\n============== HISTORIQUE GLOBAL ==============")
        history.zipWithIndex.foreach: (entry, i) =>
          output(s"  ${i + 1}. $entry")
        output("===============================================\n")
        recording(context, history, accounts, output)

      case PrintBalances =>
        if accounts.isEmpty then
          output("(aucun compte enregistré)")
          Behaviors.same
        else
          val order = accounts.keys.toVector.sorted
          val collector = context.spawnAnonymous(
            collectBalancesOrdered(accounts.keys.toSet, Map.empty, order, output)
          )
          accounts.foreach: (_, ref) =>
            ref ! GetBalance(collector)
          Behaviors.same

  // Collecte ordonnée
  private def collectBalancesOrdered(
                                      pending:  Set[String],
                                      received: Map[String, Double],
                                      order:    Vector[String],
                                      output:   String => Unit
                                    ): Behavior[BalanceResponse] =
    Behaviors.withTimers: timers =>
      timers.startSingleTimer("timeout", BalanceTimeout, 3.seconds)
      collectingOrdered(pending, received, order, output)

  private def collectingOrdered(
                                 pending:  Set[String],
                                 received: Map[String, Double],
                                 order:    Vector[String],
                                 output:   String => Unit
                               ): Behavior[BalanceResponse] =
    Behaviors.receiveMessage:

      case BalanceResult(id, balance) =>
        val updated   = received + (id -> balance)
        val remaining = pending - id
        if remaining.isEmpty then
          printOrdered(updated, order, output)
          Behaviors.stopped
        else
          collectingOrdered(remaining, updated, order, output)

      case BalanceTimeout =>
        output(s"[WARN] timeout — réponses manquantes : ${pending.mkString(", ")}")
        if received.nonEmpty then printOrdered(received, order, output)
        Behaviors.stopped

  private def printOrdered(
                            balances: Map[String, Double],
                            order:    Vector[String],
                            output:   String => Unit
                          ): Unit =
    output("=== soldes après virement =====================")
    order.foreach: id =>
      balances.get(id).foreach: b =>
        output(f"  $id%-12s : $b%.1f EUR")
    output("===============================================")
