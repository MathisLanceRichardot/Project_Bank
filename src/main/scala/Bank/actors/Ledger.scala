package Bank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import Bank.models.*

object Ledger {

  def apply(): Behavior[LedgerMessage] =
    recording(history = Vector.empty, accounts = Map.empty)

  private def recording(
                         history:  Vector[String],
                         accounts: Map[String, ActorRef[BankMessage]]
                       ): Behavior[LedgerMessage] =
    Behaviors.receiveMessage {

      case RegisterAccount(id, ref) =>
        recording(history, accounts + (id -> ref))

      case LogEvent(message) =>
        recording(history :+ message, accounts)

      case LogDebit(account, amount, newBalance) =>
        val entry = s"DEBIT    | $account | montant=$amount | nouveau solde=$newBalance"
        recording(history :+ entry, accounts)

      case LogCredit(account, amount, newBalance) =>
        val entry = s"CREDIT   | $account | montant=$amount | nouveau solde=$newBalance"
        recording(history :+ entry, accounts)

      case LogTransfer(from, to, amount, Success) =>
        val entry = s"CONFIRME | $from -> $to | montant=$amount"
        recording(history :+ entry, accounts)

      case LogTransfer(from, to, amount, Failure) =>
        val entry = s"ECHEC    | $from -> $to | montant=$amount"
        recording(history :+ entry, accounts)

      case PrintHistory =>
        println("\n============== HISTORIQUE GLOBAL ==============")
        history.zipWithIndex.foreach { case (entry, i) =>
          println(s"  ${i + 1}. $entry")
        }
        println("===============================================\n")
        Behaviors.same

      case PrintBalances =>
        Behaviors.setup { context =>
          val balanceCollector = context.spawnAnonymous(
            collectBalances(accounts.keys.toSet, Map.empty)
          )
          accounts.foreach { case (id, ref) =>
            ref ! GetBalance(balanceCollector)
          }
          Behaviors.same
        }
    }

  private def collectBalances(
                               pending:  Set[String],
                               received: Map[String, Double]
                             ): Behavior[BalanceResponse] =
    Behaviors.receiveMessage {
      case BalanceResult(id, balance) =>
        val updated   = received + (id -> balance)
        val remaining = pending - id
        if (remaining.isEmpty) {
          println("============== SOLDES FINAUX ==================")
          updated.toSeq.sortBy(_._1).foreach { case (id, balance) =>
            println(s"  $id : $balance EUR")
          }
          println("===============================================\n")
          Behaviors.stopped
        } else {
          collectBalances(remaining, updated)
        }
    }
}