package Bank.petri

object PetriNetAnalyzer {

  // ── Franchissabilité ─────────────────────────────────────────────────────
  def isFireable(net: PetriNet, transitionId: String): Boolean = {
    val transition = net.transitions.find(_.id == transitionId).get

    val tokensOk = net.arcs
      .filter(_.to == transitionId)
      .forall(arc => net.marking.getOrElse(arc.from, 0) >= arc.weight)

    tokensOk && transition.guard(net.marking)
  }

  // ── Franchissement — Either pour porter l'erreur ──────────────────────
  def fire(net: PetriNet, transitionId: String): Either[String, PetriNet] = {
    if (!isFireable(net, transitionId))
      Left(s"Transition $transitionId non franchissable")
    else {
      val inputArcs  = net.arcs.filter(_.to   == transitionId)
      val outputArcs = net.arcs.filter(_.from == transitionId)

      val newMarking = net.marking.map { case (placeId, tokens) =>
        val consumed = inputArcs.find(_.from == placeId).map(_.weight).getOrElse(0)
        val produced = outputArcs.find(_.to  == placeId).map(_.weight).getOrElse(0)
        placeId -> (tokens - consumed + produced)
      }

      Right(net.copy(marking = newMarking))
    }
  }

  // ── Espace d'états — récursion terminale avec accumulateur ───────────
  def reachableMarkings(net: PetriNet): Vector[Map[String, Int]] = {

    def explore(
                 current: Map[String, Int],
                 visited: Set[Map[String, Int]]
               ): Set[Map[String, Int]] =
      if (visited.contains(current)) visited
      else
        net.transitions.foldLeft(visited + current) { (acc, transition) =>
          fire(net.copy(marking = current), transition.id) match {
            case Right(fired) => explore(fired.marking, acc)
            case Left(_)      => acc
          }
        }

    explore(net.marking, Set.empty).toVector
  }

  // ── Deadlocks — pipeline fonctionnel ────────────────────────────────
  def detectDeadlocks(net: PetriNet): Vector[Map[String, Int]] =
    reachableMarkings(net)
      .filter(marking =>
        net.transitions.forall(t => !isFireable(net.copy(marking = marking), t.id))
      )

  // ── P-invariants — combinaisons fonctionnelles ───────────────────────
  def computePInvariants(net: PetriNet): Vector[String] = {
    val transitionIds = net.transitions.map(_.id)

    val incidence: Map[String, Map[String, Int]] =
      net.places.map { place =>
        place.id -> transitionIds.map { transId =>
          val produced = net.arcs.find(a => a.from == transId && a.to   == place.id).map(_.weight).getOrElse(0)
          val consumed = net.arcs.find(a => a.from == place.id && a.to  == transId).map(_.weight).getOrElse(0)
          transId -> (produced - consumed)
        }.toMap
      }.toMap

    net.places.toList
      .combinations(2)
      .collect {
        case List(p1, p2) if transitionIds.forall(t => incidence(p1.id)(t) + incidence(p2.id)(t) == 0) =>
          val sum = net.marking.getOrElse(p1.id, 0) + net.marking.getOrElse(p2.id, 0)
          s"P-invariant : m(${p1.name}) + m(${p2.name}) = $sum"
      }
      .toVector
  }

  // ── Propriétés — chaque vérification retourne un String ──────────────
  private def checkNoNegativeTokens(markings: Vector[Map[String, Int]]): String =
    if (markings.exists(_.values.exists(_ < 0)))
      "VIOLATION : marquage negatif detecte"
    else
      "OK : aucun marquage negatif — soldes toujours >= 0"

  private def checkMutex(net: PetriNet, markings: Vector[Map[String, Int]]): String =
    net.places
      .find(_.name.toLowerCase.contains("mutex"))
      .fold("INFO : aucune place mutex identifiee") { mutex =>
        if (markings.exists(m => m.getOrElse(mutex.id, 0) > 1))
          s"VIOLATION : ${mutex.name} depasse 1 jeton"
        else
          s"OK : ${mutex.name} contient toujours au plus 1 jeton"
      }

  private def checkNoDeadlock(net: PetriNet): String =
    detectDeadlocks(net) match {
      case Vector() => "OK : aucun deadlock detecte"
      case deadlocks => s"VIOLATION : ${deadlocks.size} deadlock(s) detecte(s)"
    }

  private def checkLiveness(net: PetriNet, markings: Vector[Map[String, Int]]): String = {
    val deadTransitions = net.transitions.filterNot(t =>
      markings.exists(m => isFireable(net.copy(marking = m), t.id))
    )
    if (deadTransitions.isEmpty)
      "OK : toutes les transitions sont franchissables — systeme vivant"
    else
      s"ATTENTION : transitions mortes : ${deadTransitions.map(_.name).mkString(", ")}"
  }

  def verifyProperties(net: PetriNet): Vector[String] = {
    val markings = reachableMarkings(net)
    Vector(
      checkNoNegativeTokens(markings),
      checkMutex(net, markings),
      checkNoDeadlock(net),
      checkLiveness(net, markings)
    )
  }

  // ── Rapport ───────────────────────────────────────────────────────────
  def analyze(net: PetriNet): Unit = {
    val markings   = reachableMarkings(net)
    val invariants = computePInvariants(net)
    val properties = verifyProperties(net)
    val deadlocks  = detectDeadlocks(net)

    val report = Seq(
      "\n================================================",
      "        ANALYSE DU RESEAU DE PETRI",
      "================================================",
      s"\n-- Structure --",
      s"  Places      : ${net.places.size}",
      s"  Transitions : ${net.transitions.size}",
      s"  Arcs        : ${net.arcs.size}",
      s"\n-- Marquage initial --",
    ) ++
      net.places.map(p => s"  ${p.name} : ${net.marking.getOrElse(p.id, 0)} jeton(s)") ++
      Seq(s"\n-- Espace d'etats --",
        s"  Marquages atteignables : ${markings.size}",
        s"\n-- P-invariants --"
      ) ++
      (if (invariants.isEmpty) Seq("  Aucun P-invariant detecte") else invariants.map(i => s"  $i")) ++
      Seq(s"\n-- Proprietes --") ++
      properties.map(p => s"  $p") ++
      (if (deadlocks.nonEmpty)
        Seq(s"\n-- Marquages deadlock --") ++
          deadlocks.map(d => s"  ${d.map { case (k, v) => s"$k=$v" }.mkString(", ")}")
      else Seq.empty) ++
      Seq("\n================================================\n")

    report.foreach(println)
  }
}