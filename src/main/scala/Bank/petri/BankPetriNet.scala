package Bank.petri

object BankPetriNet {

  // Seuils métier
  val Plafond:      Int = 1000
  val MontantA2B:   Int = 200   // montant simulé du virement
  val SoldeInitialA: Int = 800
  val SoldeInitialB: Int = 500

  val net: PetriNet = PetriNet(

    places = Vector(
      Place("p0", "Account_A"),
      Place("p1", "Demand_A2B"),
      Place("p2", "Debit_A_en_cours"),
      Place("p3", "A_debite"),
      Place("p4", "Credit_B_en_cours"),
      Place("p5", "B_credite"),
      Place("p6", "Echec_A2B"),
      Place("p7", "Account_B"),
      Place("p8", "Mutex"),
      Place("p9", "SoldeA"),   // place représentant le solde de A
      Place("p10", "SoldeB")   // place représentant le solde de B
    ),

    transitions = Vector(

      Transition("t0", "Initialiser_A2B"),

      // Validation : solde suffisant ET montant <= plafond
      Transition(
        id    = "t1",
        name  = "Valider_A2B",
        guard = marking =>
          marking.getOrElse("p9", 0) >= MontantA2B &&
            MontantA2B <= Plafond
      ),

      // Echec : solde insuffisant OU montant > plafond
      Transition(
        id    = "t2",
        name  = "Echec_A2B",
        guard = marking =>
          marking.getOrElse("p9", 0) < MontantA2B ||
            MontantA2B > Plafond
      ),

      Transition("t3", "Debiter_A"),
      Transition("t4", "Crediter_B"),
      Transition("t5", "Confirmer_A2B")
    ),

    arcs = Vector(
      // Initialisation
      Arc("p0", "t0"), Arc("p8", "t0"),
      Arc("t0", "p1"),

      // Validation
      Arc("p1", "t1"), Arc("t1", "p2"), Arc("t1", "p4"),

      // Echec
      Arc("p1", "t2"), Arc("t2", "p0"), Arc("t2", "p8"),

      // Debit A
      Arc("p2", "t3"), Arc("t3", "p3"),
      Arc("p9", "t3", MontantA2B),              // consomme MontantA2B jetons de SoldeA
      Arc("t3", "p9", 0),                        // solde réduit

      // Credit B
      Arc("p4", "t4"), Arc("t4", "p5"),
      Arc("t4", "p10", MontantA2B),              // produit MontantA2B jetons dans SoldeB

      // Confirmation
      Arc("p3", "t5"), Arc("p5", "t5"),
      Arc("t5", "p0"), Arc("t5", "p7"), Arc("t5", "p8")
    ),

    marking = Map(
      "p0"  -> 1,
      "p1"  -> 0,
      "p2"  -> 0,
      "p3"  -> 0,
      "p4"  -> 0,
      "p5"  -> 0,
      "p6"  -> 0,
      "p7"  -> 1,
      "p8"  -> 1,
      "p9"  -> SoldeInitialA,   // 800 jetons = solde de A
      "p10" -> SoldeInitialB    // 500 jetons = solde de B
    )
  )
}