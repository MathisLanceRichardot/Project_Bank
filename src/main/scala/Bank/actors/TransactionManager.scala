package Bank.actors

import akka.actor.Actor
import akka.actor.ActorRef
import Bank.models._

class TransactionManager(account: ActorRef,
                         fraud: ActorRef,
                         ledger: ActorRef) extends Actor {

  def receive: Receive = {
    case t: Deposit =>
      fraud ! ValidateTransaction(t)

    case w: Withdraw =>
      fraud ! ValidateTransaction(w)

    case TransactionApproved(tx) =>
      tx match {
        case d: Deposit  => account ! d
        case w: Withdraw => account ! w
      }
      ledger ! LogsTransaction(s"Transaction OK: ${tx.user} ${tx.amount}")

    case TransactionRejected(tx, reason) =>
      println(s"[TransactionManager] Transaction refusee pour ${tx.user}: $reason")
      ledger ! LogsTransaction(s"Transaction refusee: ${tx.user} $reason")
  }
}