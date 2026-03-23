package Bank.models

sealed trait Transaction {
  def user : String
  def amount : Double
}

case class CreateAccount(user : String)
case class Deposit(user: String,amount : Double) extends Transaction
case class Withdraw(user: String,amount : Double) extends Transaction
case class Transfer(form : String, to : String, amount : Double)
case class ValidateTransaction(tx: Transaction)
case class TransactionApproved(tx: Transaction)
case class TransactionRejected(tx: Transaction, reason: String)
case class LogsTransaction(message : String)
