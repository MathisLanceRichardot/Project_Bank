package Bank

import akka.actor.typed.{ActorSystem, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration.*
import Bank.actors.*
import Bank.models.*


object Main extends App {

  val root = Behaviors.setup[Nothing] { context =>

    val ledger = context.spawn(Ledger(), "Ledger")

    // ── Supervision : restart automatique en cas d'exception ─────────────────
    val supervisedAccount = (id: String, balance: Double) =>
      Behaviors
        .supervise(BankAccount(id, balance, ledger))
        .onFailure[Exception](SupervisorStrategy.restart)

    val alice   = context.spawn(supervisedAccount("Alice",   800.0), "Alice")
    val clement    = context.spawn(supervisedAccount("Clement",     500.0), "Clement")
    val vincent = context.spawn(supervisedAccount("Vincent", 300.0), "Vincent")

    ledger ! RegisterAccount("Alice",   alice)
    ledger ! RegisterAccount("Clement",     clement)
    ledger ! RegisterAccount("Vincent", vincent)

    // ── Supervision du TransferManager : resume en cas d'erreur non critique ──
    val supervisedTransfer =
      Behaviors
        .supervise(TransferManager(ledger))
        .onFailure[Exception](SupervisorStrategy.resume)

    val transfer = context.spawn(supervisedTransfer, "TransferManager")

    transfer ! TransferRequest(alice,   clement,     200.0)
    transfer ! TransferRequest(clement,     vincent, 150.0)
    transfer ! TransferRequest(vincent, alice,   500.0)
    transfer ! TransferRequest(alice,   clement,    1500.0)


    Behaviors.empty
  }

  val system = ActorSystem[Nothing](root, "BankSystem")
  Thread.sleep(4000)
  system.terminate()
}