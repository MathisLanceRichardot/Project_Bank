package Bank

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import Bank.actors.*
import Bank.models.*

class TransferManagerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

  private val dummyLedger = testKit.spawn(
    akka.actor.typed.scaladsl.Behaviors.receiveMessage[LedgerMessage] { _ =>
      akka.actor.typed.scaladsl.Behaviors.same
    },
    "DummyLedger-Transfer"
  )

  "TransferManager" should {

    "effectuer un transfert valide entre deux comptes" in {
      val alice    = testKit.spawn(BankAccount("Alice",  800.0, dummyLedger), "Alice-T1")
      val bob      = testKit.spawn(BankAccount("Bob",    500.0, dummyLedger), "Bob-T1")
      val transfer = testKit.spawn(TransferManager(dummyLedger), "Transfer-1")

      val probeAlice = testKit.createTestProbe[BalanceResponse]()
      val probeBob   = testKit.createTestProbe[BalanceResponse]()

      transfer ! TransferRequest(alice, bob, 200.0)

      Thread.sleep(300)

      alice ! GetBalance(probeAlice.ref)
      bob   ! GetBalance(probeBob.ref)

      probeAlice.expectMessage(BalanceResult("Alice", 600.0))
      probeBob.expectMessage(BalanceResult("Bob",    700.0))
    }

    "ne pas modifier les soldes si le debit echoue" in {
      val charlie  = testKit.spawn(BankAccount("Charlie", 100.0, dummyLedger), "Charlie-T1")
      val alice    = testKit.spawn(BankAccount("Alice",   800.0, dummyLedger), "Alice-T2")
      val transfer = testKit.spawn(TransferManager(dummyLedger), "Transfer-2")

      val probeCharlie = testKit.createTestProbe[BalanceResponse]()

      transfer ! TransferRequest(charlie, alice, 500.0)

      Thread.sleep(300)

      charlie ! GetBalance(probeCharlie.ref)
      probeCharlie.expectMessage(BalanceResult("Charlie", 100.0))
    }

    "ne pas modifier les soldes si le montant depasse le plafond" in {
      val alice    = testKit.spawn(BankAccount("Alice", 5000.0, dummyLedger), "Alice-T3")
      val bob      = testKit.spawn(BankAccount("Bob",   500.0,  dummyLedger), "Bob-T3")
      val transfer = testKit.spawn(TransferManager(dummyLedger), "Transfer-3")

      val probeAlice = testKit.createTestProbe[BalanceResponse]()

      transfer ! TransferRequest(alice, bob, 1500.0)

      Thread.sleep(300)

      alice ! GetBalance(probeAlice.ref)
      probeAlice.expectMessage(BalanceResult("Alice", 5000.0))
    }

    "traiter plusieurs transferts sequentiellement dans l'ordre" in {
      val alice    = testKit.spawn(BankAccount("Alice",   800.0, dummyLedger), "Alice-T4")
      val bob      = testKit.spawn(BankAccount("Bob",     500.0, dummyLedger), "Bob-T4")
      val charlie  = testKit.spawn(BankAccount("Charlie", 300.0, dummyLedger), "Charlie-T4")
      val transfer = testKit.spawn(TransferManager(dummyLedger), "Transfer-4")

      val probeAlice   = testKit.createTestProbe[BalanceResponse]()
      val probeBob     = testKit.createTestProbe[BalanceResponse]()
      val probeCharlie = testKit.createTestProbe[BalanceResponse]()

      transfer ! TransferRequest(alice, bob,     200.0)
      transfer ! TransferRequest(bob,   charlie, 150.0)

      Thread.sleep(500)

      alice   ! GetBalance(probeAlice.ref)
      bob     ! GetBalance(probeBob.ref)
      charlie ! GetBalance(probeCharlie.ref)

      probeAlice.expectMessage(BalanceResult("Alice",   600.0))
      probeBob.expectMessage(BalanceResult("Bob",       550.0))
      probeCharlie.expectMessage(BalanceResult("Charlie", 450.0))
    }
  }
}