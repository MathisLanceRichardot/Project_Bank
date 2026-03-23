package Bank.actors

import akka.actor.Actor
import Bank.models._

class Ledger extends Actor{
  def receive : Receive ={
    case LogsTransaction(msg) =>
      println(s"[Ledger] $msg")
  }
}
