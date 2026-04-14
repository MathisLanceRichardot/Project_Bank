package Bank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import Bank.models.*

object BankAccount {

  val Plafond: Double = 1000.0

  def apply(id: String, initialBalance: Double, ledger: ActorRef[LedgerMessage]): Behavior[BankMessage] =
    active(id, initialBalance, ledger)

  private def active(
                      id:      String,
                      balance: Double,
                      ledger:  ActorRef[LedgerMessage]
                    ): Behavior[BankMessage] =
    Behaviors.receiveMessage {
      case Debit(amount, replyTo) =>
        if (balance >= amount && amount <= Plafond) {
          ledger ! LogDebit(id, amount, balance - amount)
          replyTo ! DebitSuccess
          active(id, balance - amount, ledger)
        } else {
          val reason = if (amount > Plafond) " plafond depasse" else " solde insuffisant"
          ledger ! LogEvent(s"ECHEC_DEBIT | $id | montant=$amount | raison=$reason")
          replyTo ! DebitFailure
          Behaviors.same
        }

      case Credit(amount, replyTo) =>
        ledger ! LogCredit(id, amount, balance + amount)
        replyTo ! CreditSuccess
        active(id, balance + amount, ledger)

      case GetBalance(replyTo) =>
        replyTo ! BalanceResult(id, balance)
        Behaviors.same
    }
}