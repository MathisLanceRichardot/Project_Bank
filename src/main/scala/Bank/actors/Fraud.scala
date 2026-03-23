package Bank.actors

import akka.actor.Actor
import Bank.models._

class Fraud extends Actor {
  def receive: Receive = {
    case ValidateTransaction(tx) =>
      tx match {
        case t: Transaction =>
          if (t.amount > 1000) sender() ! TransactionRejected(t, "Montant trop eleve")
          else sender() ! TransactionApproved(t)
      }
  }
}
