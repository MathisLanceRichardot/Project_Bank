# 🏦 Projet Bank - Système de Transferts Akka & ScalaFX

Ce projet est une simulation de système bancaire réactif développée en **Scala 3**. Il utilise le modèle d'acteurs **Akka** pour la gestion de la logique métier et **ScalaFX** pour l'interface graphique utilisateur (GUI).

---

## 🛠️ 1. Installation et Configuration

Le projet a été développé sous **JDK 21/24**. L'utilisation d'IntelliJ Ultimate est recommandée car il gère nativement SBT.

### Importation du projet

1. **Cloner le dépôt :**
```bash
   git clone https://github.com/TON_NOM_UTILISATEUR/Project_Bank.git
```

2. **Ouvrir avec IntelliJ :** `File > Open` → sélectionnez le dossier racine du projet.

3. **Charger SBT :** Cliquez sur `Load sbt Project` dans la notification. Si l'onglet SBT est vide, cliquez sur l'icône "Reload".

4. **Vérifier le SDK :**
   - `File > Project Structure` (`Ctrl+Alt+Shift+S`)
   - Project SDK réglé sur **21 ou plus**
   - Dans `Modules`, vérifiez que `src/main/scala` est marqué en bleu (Sources)

---

## 💻 2. Guide SBT

SBT gère les dépendances et la compilation. Utilisez le sbt shell d'IntelliJ ou installez-le sur votre système.

### Installation de SBT

**Ubuntu / Linux :**
```bash
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update && sudo apt-get install sbt
```

**Windows :** Téléchargez l'installeur `.msi` sur [scala-sbt.org](https://www.scala-sbt.org).

### Commandes indispensables

| Commande | Action |
|---|---|
| `sbt run` | Compile et lance l'interface graphique |
| `sbt test` | Exécute les tests unitaires (Akka TestKit) |
| `sbt clean` | Nettoie les fichiers temporaires |
| `sbt stage` | Prépare les scripts de lancement natifs |
| `sbt assembly` | Génère un Fat JAR |

### Déploiement avec `sbt stage`

```bash
sbt stage
cd target/universal/stage/bin/
./project_bank        # Linux
project_bank.bat      # Windows
```

---

## 🧪 3. Tests et Qualité

Le projet inclut une suite de tests unitaires avec **Akka TestKit** validant :

- L'atomicité des transferts entre comptes
- Le respect des plafonds de retrait
- La cohérence du solde après plusieurs opérations simultanées

```bash
# Terminal
sbt test

# IntelliJ
clic droit sur src/test/scala > Run 'All Tests'
```

---

## ⚠️ 4. Résolution des problèmes

### Erreur `UnsupportedClassVersionError`

Votre terminal utilise une version Java trop ancienne. Forcez Java 21 :

```bash
# Linux
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Windows
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```## 📂 Structure du Projet

L'organisation des fichiers suit les conventions standard de **sbt** avec une séparation claire entre la logique métier (Acteurs), les modèles formels (Pétri) et l'interface utilisateur.

```text
Project_Bank/
├── src/
│   ├── main/
│   │   └── scala/
│   │       └── Bank/
│   │           ├── actors/    # Logique métier (BankAccount, TransferManager, Ledger)
│   │           ├── models/    # Protocoles de messages (Commands, Events, Responses)
│   │           └── ui/        # Interface graphique ScalaFX (Vues et Contrôleurs)
│   └── test/
│       └── scala/
│           └── Bank/          # Tests unitaires et d'intégration (Akka TestKit)
│               ├── BankAccountSpec.scala
│               └── TransferManagerSpec.scala
├── project/
│   ├── build.properties       # Version de sbt
│   └── plugins.sbt            # Plugins (sbt-assembly, native-packager)
├── build.sbt                  # Configuration des dépendances (Akka, ScalaFX)
└── README.md                  # Documentation du projet
