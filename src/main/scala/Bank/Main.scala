package Bank

import akka.actor.ActorSystem
import akka.actor.Props
import Bank.actors._
import Bank.models._

object Main extends App {
  val system = ActorSystem("BankSystem")

  val account = system.actorOf(Props[Account], "account")
  val ledger = system.actorOf(Props[Ledger], "ledger")
  val fraud = system.actorOf(Props[Fraud], "fraud")

  val transactionManager =
    system.actorOf(Props(new TransactionManager(account, fraud, ledger)), "txManager")

  // Tests
  transactionManager ! Deposit("user1", 500)
  transactionManager ! Withdraw("user1", 200)
  transactionManager ! Deposit("user2", 1500)
  transactionManager ! Withdraw("user1", 400)
}