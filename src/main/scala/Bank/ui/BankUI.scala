package Bank.ui

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.scene.paint.Color
import scalafx.scene.text.{Font, FontWeight}
import scalafx.collections.ObservableBuffer
import Bank.actors.*
import Bank.models.*
import scalafx.Includes.jfxPriority2sfx

object BankUI extends JFXApp3 {

  // ── Limite maximale de comptes ────────────────────────────────────────────
  private val MAX_ACCOUNTS = 5

  private var akkaSystem:    Option[ActorSystem[Nothing]]                        = None
  private var transferActor: Option[akka.actor.typed.ActorRef[TransferMessage]]  = None
  private var ledgerActor:   Option[akka.actor.typed.ActorRef[LedgerMessage]]    = None
  private var accountActors: Map[String, akka.actor.typed.ActorRef[BankMessage]] = Map.empty

  // Ordre de création conservé pour l'affichage trié
  private var creationOrder: Vector[String] = Vector.empty

  // Historique complet des logs UI — permet de tout réafficher
  private var logHistory: Vector[String] = Vector.empty

  // Garde contre les double-clics sur le bouton Virer
  private var transferInProgress: Boolean = false

  private var fromComboRef: Option[ComboBox[String]] = None
  private var toComboRef:   Option[ComboBox[String]] = None

  private class ProgressiveOutput {
    private var delay = 0L

    val emit: String => Unit = message => {
      delay += 300
      val d = delay
      new Thread(() => {
        Thread.sleep(d)
        scalafx.application.Platform.runLater {
          logArea.appendText(s"$message\n")
          logArea.scrollTop = Double.MaxValue
        }
      }).start()
    }

    def reset(): Unit = delay = 0L
  }

  private lazy val logArea = new TextArea {
    editable = false
    wrapText = true
    style    = """
      -fx-background-color: #ffffff;
      -fx-text-fill: #1e1e1e;
      -fx-font-family: monospace;
      -fx-font-size: 13;
      -fx-control-inner-background: #ffffff;
    """
  }

  override def start(): Unit =
    stage = new PrimaryStage {
      title  = "Bank Simulation - Akka"
      width  = 900
      height = 700
      scene  = new Scene {
        fill = Color.rgb(30, 30, 30)
        stylesheets.add(getClass.getResource("/bank.css").toExternalForm)
        root = buildUI()
      }
    }

  private def buildUI(): VBox =
    new VBox(15) {
      padding = Insets(20)
      style   = "-fx-background-color: #1e1e1e;"
      children = Seq(
        new Label("Bank Simulation") {
          font  = Font.font("Arial", FontWeight.Bold, 24)
          style = "-fx-text-fill: #61dafb;"
        },
        buildSetupPanel(),
        buildTransferPanel(),
        buildLogPanel()
      )
    }

  private def buildSetupPanel(): VBox =
    new VBox(10) {
      style = "-fx-background-color: #2d2d2d; -fx-padding: 15; -fx-background-radius: 8;"

      val countField = new TextField {
        text     = "3"
        maxWidth = 60
        style    = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
      }

      // Label d'avertissement sur la limite
      val limitLabel = new Label(s"(max $MAX_ACCOUNTS comptes)") {
        style = "-fx-text-fill: #e67e22; -fx-font-size: 11;"
      }

      val accountsContainer = new VBox(8)

      def generateFields(n: Int): Unit = {
        // On clamp silencieusement à MAX_ACCOUNTS
        val clamped = n.min(MAX_ACCOUNTS)
        accountsContainer.children.clear()
        (1 to clamped).foreach { i =>
          val nameField = new TextField {
            promptText = s"Nom compte $i"
            text       = s"Compte$i"
            prefWidth  = 150
            style      = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
          }
          val balanceField = new TextField {
            promptText = "Solde initial"
            text       = "500"
            prefWidth  = 100
            style      = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
          }
          accountsContainer.children.add(
            new HBox(10) {
              children = Seq(
                new Label(s"Compte $i :") { style = "-fx-text-fill: #aaaaaa;" },
                nameField,
                new Label("Solde :") { style = "-fx-text-fill: #aaaaaa;" },
                balanceField
              )
            }
          )
        }
      }

      generateFields(3)

      countField.text.onChange { (_, _, newVal) =>
        newVal.toIntOption.foreach { n =>
          if n > MAX_ACCOUNTS then
            // On remet le champ à la limite et on avertit
            scalafx.application.Platform.runLater {
              countField.text = MAX_ACCOUNTS.toString
            }
            generateFields(MAX_ACCOUNTS)
          else if n > 0 then
            generateFields(n)
        }
      }

      children = Seq(
        new Label("1. Initialiser les comptes") {
          font  = Font.font("Arial", FontWeight.Bold, 14)
          style = "-fx-text-fill: #ffffff;"
        },
        new HBox(10) {
          children = Seq(
            new Label("Nombre de comptes :") { style = "-fx-text-fill: #cccccc;" },
            countField,
            limitLabel
          )
        },
        accountsContainer,
        new Button("Demarrer le systeme Akka") {
          style    = """
            -fx-background-color: #61dafb;
            -fx-text-fill: #1e1e1e;
            -fx-font-weight: bold;
            -fx-background-radius: 5;
            -fx-cursor: hand;
          """
          onAction = _ => startAkka(accountsContainer)
        }
      )
    }

  private def buildTransferPanel(): VBox =
    new VBox(10) {
      style = "-fx-background-color: #2d2d2d; -fx-padding: 15; -fx-background-radius: 8;"

      val fromCombo = new ComboBox[String] {
        promptText = "Compte emetteur"
        style      = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
      }
      val toCombo = new ComboBox[String] {
        promptText = "Compte destinataire"
        style      = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
      }
      val amountField = new TextField {
        promptText = "Montant"
        prefWidth  = 100
        style      = "-fx-background-color: #3d3d3d; -fx-text-fill: white;"
      }

      fromCombo.value.onChange { (_, _, newVal) =>
        Option(newVal).foreach(_ =>
          toCombo.items = ObservableBuffer(accountActors.keys.filter(_ != newVal).toSeq*)
        )
      }
      toCombo.value.onChange { (_, _, newVal) =>
        Option(newVal).foreach(_ =>
          fromCombo.items = ObservableBuffer(accountActors.keys.filter(_ != newVal).toSeq*)
        )
      }

      fromComboRef = Some(fromCombo)
      toComboRef   = Some(toCombo)

      children = Seq(
        new Label("2. Effectuer un virement") {
          font  = Font.font("Arial", FontWeight.Bold, 14)
          style = "-fx-text-fill: #ffffff;"
        },
        new HBox(10) {
          children = Seq(
            fromCombo,
            new Label("->") { style = "-fx-text-fill: white;" },
            toCombo,
            new Label("Montant :") { style = "-fx-text-fill: #aaaaaa;" },
            amountField,
            new Button("Virer") {
              style    = """
                -fx-background-color: #4caf50;
                -fx-text-fill: white;
                -fx-font-weight: bold;
                -fx-background-radius: 5;
                -fx-cursor: hand;
              """
              onAction = _ => {
                if !transferInProgress then
                  val from   = fromCombo.value.value
                  val to     = toCombo.value.value
                  val amount = amountField.text.value.toDoubleOption.getOrElse(0.0)

                  (transferActor, accountActors.get(from), accountActors.get(to)) match {
                    case (Some(transfer), Some(fromRef), Some(toRef)) if from != to && amount > 0 =>
                      transferInProgress = true
                      transfer ! TransferRequest(fromRef, toRef, amount)
                      addLog(s">> Virement demande : $from -> $to | $amount EUR")
                      // Déverrouille après un délai suffisant pour qu'Akka traite le message
                      new Thread(() => {
                        Thread.sleep(800)
                        scalafx.application.Platform.runLater {
                          transferInProgress = false
                        }
                      }).start()
                    case _ =>
                      addLog("!! Virement invalide : verifiez les comptes et le montant")
                  }
              }
            }
          )
        }
      )
    }

  private def buildLogPanel(): VBox =
    new VBox(8) {
      VBox.setVgrow(this, javafx.scene.layout.Priority.ALWAYS)
      VBox.setVgrow(logArea, javafx.scene.layout.Priority.ALWAYS)
      style = "-fx-background-color: #2d2d2d; -fx-padding: 15; -fx-background-radius: 8;"

      children = Seq(
        new Label("3. Logs") {
          font  = Font.font("Arial", FontWeight.Bold, 14)
          style = "-fx-text-fill: #ffffff;"
        },
        logArea,
        new HBox(10) {
          children = Seq(
            new Button("Effacer les logs") {
              style    = "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5; -fx-cursor: hand;"
              onAction = _ => logArea.clear()
            },
            new Button("Reafficher les logs") {
              style    = "-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"
              onAction = _ => {
                logArea.clear()
                logHistory.foreach(line => logArea.appendText(s"$line\n"))
                logArea.scrollTop = Double.MaxValue
              }
            },
            new Button("Afficher les soldes") {
              style    = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"
              onAction = _ => ledgerActor match {
                case Some(ledger) =>
                  // ── 2. PrintBalances dans l'ordre de création ─────────────
                  ledger ! PrintBalancesOrdered(creationOrder)
                case None =>
                  addLog("!! Systeme non demarre")
              }
            },
            new Button("Reset") {
              style    = "-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand;"
              onAction = _ => resetSystem()
            }
          )
        }
      )
    }

  private def resetSystem(): Unit = {
    akkaSystem.foreach(_.terminate())
    akkaSystem         = None
    transferActor      = None
    ledgerActor        = None
    accountActors      = Map.empty
    creationOrder      = Vector.empty
    logHistory         = Vector.empty
    transferInProgress = false
    fromComboRef.foreach(_.items = ObservableBuffer.empty)
    toComboRef.foreach(_.items   = ObservableBuffer.empty)
    fromComboRef.foreach(_.value = null)
    toComboRef.foreach(_.value   = null)
    logArea.clear()
    addLog("Systeme reinitialise — redemarrez avec de nouveaux comptes")
  }

  private def startAkka(container: VBox): Unit = {
    akkaSystem.foreach(_.terminate())
    accountActors = Map.empty
    creationOrder = Vector.empty
    logHistory    = Vector.empty
    logArea.clear()

    val accounts: Seq[(String, Double)] = container.children
      .collect { case row: javafx.scene.layout.HBox => row }
      .flatMap { row =>
        row.getChildren
          .filtered(_.isInstanceOf[javafx.scene.control.TextField])
          .toArray
          .collect { case f: javafx.scene.control.TextField => f.getText }
          .toSeq match {
          case Seq(name, balance) => balance.toDoubleOption.map(name -> _)
          case _                  => None
        }
      }
      .toSeq

    if accounts.isEmpty then
      addLog("!! Aucun compte valide a initialiser")
    else {
      val progressiveOutput = new ProgressiveOutput

      val root = Behaviors.setup[Nothing] { context =>

        val ledger = context.spawn(
          Ledger(progressiveOutput.emit),
          "Ledger"
        )
        ledgerActor = Some(ledger)

        // ── 3. On crée les comptes en conservant l'ordre ──────────────────
        accounts.foreach { case (name, balance) =>
          val ref = context.spawn(
            Behaviors
              .supervise(BankAccount(name, balance, ledger))
              .onFailure[Exception](akka.actor.typed.SupervisorStrategy.restart),
            name
          )
          accountActors = accountActors + (name -> ref)
          creationOrder = creationOrder :+ name          // ordre garanti
          ledger ! RegisterAccount(name, ref)
          ledger ! LogEvent(s"Compte cree : $name | solde initial = $balance EUR")
        }

        val transfer = context.spawn(
          Behaviors
            .supervise(TransferManager(ledger))
            .onFailure[Exception](akka.actor.typed.SupervisorStrategy.resume),
          "TransferManager"
        )
        transferActor = Some(transfer)
        Behaviors.empty
      }

      akkaSystem = Some(ActorSystem[Nothing](root, "BankUISystem"))

      scalafx.application.Platform.runLater {
        val names = accounts.map(_._1)
        fromComboRef.foreach(_.items = ObservableBuffer(names*))
        toComboRef.foreach(_.items   = ObservableBuffer(names*))
      }

      addLog(s"Systeme demarre avec ${accounts.size} comptes")
    }
  }

  private def addLog(message: String): Unit =
    logHistory = logHistory :+ message
    scalafx.application.Platform.runLater {
      logArea.appendText(s"$message\n")
      logArea.scrollTop = Double.MaxValue
    }
}