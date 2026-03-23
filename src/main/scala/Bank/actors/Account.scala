package Bank.actors

import akka.actor.Actor
import Bank.models._

class Account extends Actor {

  var soldes = Map.empty[String, Double];

  def receive : Receive ={
    case Deposit(user,amount) =>
      val newSolde = soldes.getOrElse(user,0.0)+amount
      soldes += user -> newSolde
      println(s"[Account] Nouveau solde : $newSolde")

    case Withdraw(user,amount) =>
      val current = soldes.getOrElse(user,0.0)
      if(amount > 0 && current >=amount ){
        val newSolde = current - amount
        soldes += user -> newSolde
        println(s"[Account] [$user] Retrait OK : $newSolde")
      }
      else {
        println(s"[Account] [$user] Retrait impossible. Solde : $current")
      }

  }
}


