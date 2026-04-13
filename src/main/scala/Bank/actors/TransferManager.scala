package Bank.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import Bank.models.*

object TransferManager {

  def apply(ledger: ActorRef[LedgerMessage]): Behavior[TransferMessage] =
    Behaviors.setup { context =>
      val adapter = context.messageAdapter[TransferResponse](WrappedResponse(_))

      def idle: Behavior[TransferMessage] =
        Behaviors.receiveMessage {
          case TransferRequest(from, to, amount) =>
            ledger ! LogEvent(s"DEMANDE  | ${from.path.name} -> ${to.path.name} | montant=$amount")
            from ! Debit(amount, adapter)
            waitingDebit(from, to, amount, queue = Vector.empty)

          case _ => Behaviors.same
        }

      def waitingDebit(
                        from:   ActorRef[BankMessage],
                        to:     ActorRef[BankMessage],
                        amount: Double,
                        queue:  Vector[TransferRequest]
                      ): Behavior[TransferMessage] =
        Behaviors.receiveMessage {
          case req: TransferRequest =>
            waitingDebit(from, to, amount, queue :+ req)

          case WrappedResponse(DebitSuccess) =>
            ledger ! LogEvent(s"DEBIT_OK | ${from.path.name} -> ${to.path.name} | montant=$amount")
            to ! Credit(amount, adapter)
            waitingCredit(from, to, amount, queue)

          case WrappedResponse(DebitFailure) =>
            ledger ! LogTransfer(from.path.name, to.path.name, amount, Failure)
            processNext(queue)

          case _ => Behaviors.same
        }

      def waitingCredit(
                         from:   ActorRef[BankMessage],
                         to:     ActorRef[BankMessage],
                         amount: Double,
                         queue:  Vector[TransferRequest]
                       ): Behavior[TransferMessage] =
        Behaviors.receiveMessage {
          case req: TransferRequest =>
            waitingCredit(from, to, amount, queue :+ req)

          case WrappedResponse(CreditSuccess) =>
            ledger ! LogTransfer(from.path.name, to.path.name, amount, Success)
            processNext(queue)

          case _ => Behaviors.same
        }

      def processNext(queue: Vector[TransferRequest]): Behavior[TransferMessage] =
        queue match {
          case head +: tail =>
            ledger ! LogEvent(s"DEMANDE  | ${head.from.path.name} -> ${head.to.path.name} | montant=${head.amount}")
            head.from ! Debit(head.amount, adapter)
            waitingDebit(head.from, head.to, head.amount, tail)

          case _ => idle
        }

      idle
    }
}