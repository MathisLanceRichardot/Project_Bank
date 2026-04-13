package Bank.models

import akka.actor.typed.ActorRef

// ── Protocole BankAccount ────────────────────────────────────────────────────
sealed trait BankMessage
case class Debit(amount: Double, replyTo: ActorRef[TransferResponse])  extends BankMessage
case class Credit(amount: Double, replyTo: ActorRef[TransferResponse]) extends BankMessage
case class GetBalance(replyTo: ActorRef[BalanceResponse])              extends BankMessage

// ── Protocole TransferManager ────────────────────────────────────────────────
sealed trait TransferMessage
case class TransferRequest(
                            from:   ActorRef[BankMessage],
                            to:     ActorRef[BankMessage],
                            amount: Double
                          ) extends TransferMessage
private[Bank] case class WrappedResponse(response: TransferResponse) extends TransferMessage

// ── Protocole Ledger ─────────────────────────────────────────────────────────
sealed trait LedgerMessage
case class LogEvent(message: String)  extends LedgerMessage
case class LogTransfer(from: String, to: String, amount: Double, status: TransferStatus) extends LedgerMessage
case class LogDebit(account: String, amount: Double, newBalance: Double)  extends LedgerMessage
case class LogCredit(account: String, amount: Double, newBalance: Double) extends LedgerMessage
case object PrintHistory extends LedgerMessage
case object PrintBalances extends LedgerMessage
case class RegisterAccount(id: String, ref: ActorRef[BankMessage]) extends LedgerMessage

sealed trait TransferStatus
case object Success extends TransferStatus
case object Failure extends TransferStatus

// ── Réponses ─────────────────────────────────────────────────────────────────
sealed trait TransferResponse
case object DebitSuccess  extends TransferResponse
case object DebitFailure  extends TransferResponse
case object CreditSuccess extends TransferResponse

sealed trait BalanceResponse
case class BalanceResult(id: String, balance: Double) extends BalanceResponse