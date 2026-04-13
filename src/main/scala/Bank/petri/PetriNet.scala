package Bank.petri

case class Place(id: String, name: String)

case class Transition(
                       id:    String,
                       name:  String,
                       guard: Map[String, Int] => Boolean = _ => true  // par défaut toujours franchissable
                     )

case class Arc(from: String, to: String, weight: Int = 1)

case class PetriNet(
                     places:      Vector[Place],
                     transitions: Vector[Transition],
                     arcs:        Vector[Arc],
                     marking:     Map[String, Int]
                   )