package Bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import Bank.actors.*
import Bank.models.*

class BankAccountSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  // ── Ledger factice qui ignore tout ───────────────────────────────────────────
  private val dummyLedger = testKit.spawn(
    akka.actor.typed.scaladsl.Behaviors.receiveMessage[LedgerMessage] { _ =>
      akka.actor.typed.scaladsl.Behaviors.same
    },
    "DummyLedger"
  )

  "BankAccount" should {

    "accepter un debit valide et retourner DebitSuccess" in {
      val probe   = testKit.createTestProbe[TransferResponse]()
      val account = testKit.spawn(BankAccount("Alice", 800.0, dummyLedger), "Alice-1")

      account ! Debit(200.0, probe.ref)

      probe.expectMessage(DebitSuccess)
    }

    "refuser un debit si solde insuffisant et retourner DebitFailure" in {
      val probe   = testKit.createTestProbe[TransferResponse]()
      val account = testKit.spawn(BankAccount("Bob", 100.0, dummyLedger), "Bob-1")

      account ! Debit(500.0, probe.ref)

      probe.expectMessage(DebitFailure)
    }

    "refuser un debit depassant le plafond de 1000 EUR et retourner DebitFailure" in {
      val probe   = testKit.createTestProbe[TransferResponse]()
      val account = testKit.spawn(BankAccount("Charlie", 5000.0, dummyLedger), "Charlie-1")

      account ! Debit(1500.0, probe.ref)

      probe.expectMessage(DebitFailure)
    }

    "accepter un credit et retourner CreditSuccess" in {
      val probe   = testKit.createTestProbe[TransferResponse]()
      val account = testKit.spawn(BankAccount("Alice", 800.0, dummyLedger), "Alice-2")

      account ! Credit(200.0, probe.ref)

      probe.expectMessage(CreditSuccess)
    }

    "maintenir un solde coherent apres plusieurs operations sequentielles" in {
      val probe   = testKit.createTestProbe[TransferResponse]()
      val account = testKit.spawn(BankAccount("Alice", 800.0, dummyLedger), "Alice-3")

      account ! Debit(200.0, probe.ref)
      probe.expectMessage(DebitSuccess)

      account ! Debit(200.0, probe.ref)
      probe.expectMessage(DebitSuccess)

      // Solde restant = 400, debit de 500 doit echouer
      account ! Debit(500.0, probe.ref)
      probe.expectMessage(DebitFailure)
    }

    "retourner le solde correct via GetBalance" in {
      val probe   = testKit.createTestProbe[BalanceResponse]()
      val account = testKit.spawn(BankAccount("Alice", 800.0, dummyLedger), "Alice-4")

      account ! Debit(200.0, testKit.createTestProbe[TransferResponse]().ref)
      account ! GetBalance(probe.ref)

      probe.expectMessage(BalanceResult("Alice", 600.0))
    }
  }
}