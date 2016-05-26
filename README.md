# À propos de l'application PAD (collaborative-editor)

* Licence : [AGPL v3](http://www.gnu.org/licenses/agpl.txt) - Copyright Conseil Régional Nord Pas de Calais - Picardie
* Développeur(s) : ATOS
* Financeur(s) : Région Nord Pas de Calais-Picardie
* Description : Application d'éditeur de texte collaboratif (Pad) qui s'appuie sur https://github.com/ether/etherpad-lite embarqué

# Documentation technique

## Prérequis

 1. L'application Pad nécessite un accès à un service Etherpad hébergé chez un tiers. Certaines informations de configuration de l'application doivent être communiquées par l'hébergeur de la solution Etherpad.
 2. Le domaine de deuxième niveau du service externe Etherpad et de l'application Pad (collaborative-editor) doit être identique.
 3. Le service Etherpad distant doit être exposé via SSL (HTTPS).

## Construction

<pre>
		gradle copyMod
</pre>

## Déployer dans ent-core


## Configuration

Dans le fichier `/collaborativeeditor/deployment/collaborativeeditor/conf.json.template` :


Déclarer l'application dans la liste :
<pre>
{
  "name": "net.atos~collaborative-editor~0.1-SNAPSHOT",
  "config": {
    "main" : "net.atos.entng.collaborativeeditor.CollaborativeEditor",
    "port" : 8668,
    "app-name" : "Collaborative Editor",
    "app-address" : "/collaborativeeditor",
    "app-icon" : "collaborativeeditor-large",
    "host": "${host}",
    "ssl" : $ssl,
    "userbook-host": "${host}",
    "integration-mode" : "HTTP",
    "app-registry.port" : 8012,
    "mode" : "${mode}",
    "etherpad-domain": "domaine.fr",
    "etherpad-url": "http://host.domaine.fr",
    "etherpad-public-url": "http://host.domaine.fr",
    "etherpad-api-key" : "clef",
    "unusedPadCron" : "0 0 23 * * ?",
    "numberDaysWithoutActivity" : 90,
    "recurringNotificationDays" : 15,
    "entcore.port" : 8009  
  }
}
</pre>
Configurer le domaine, l'url du service Etherpad et la clef pour pouvoir communiquer avec l'api et l'éditeur. Ces informations doivent être transmises par l'hébergeur :

 - "etherpad-domain": *domaine.fr* est à remplacer par "picardie.fr" dans le cadre de Léo
 
 - "etherpad-url" et "etherpad-public-url": *http://host.domaine.fr* est à remplacer par l'url de l'hébergeur du Pad (à noter que le  domaine de deuxième niveau doit être identique à celui de l'ENT)

 - "etherpad-api-key" : *clef* est à remplacer par la clef de l'hébergeur du service Pad

Associer une route d'entée à la configuration du module proxy intégré (`"name": "net.atos~collaborative-editor~0.1-SNAPSHOT"`) :
<pre>
	{
		"location": "/collaborativeeditor",
		"proxy_pass": "http://localhost:8668"
	}
</pre>



# Présentation du module

## Fonctionnalités

Le Pad est un éditeur de texte en ligne fonctionnant en mode collaboratif temps réel. 
Il vous permet de partager l'élaboration simultanée d'un texte, et d'en discuter en parallèle, via une messagerie instantanée.

Des permissions sur les différentes actions possibles sur les pads, dont la contribution et la gestion, sont configurées dans le Pad (via des partages Ent-core). 
Le droit de lecture, correspondant à qui peut consulter le Pad est également configuré de cette manière. 

Le Pad met en œuvre un comportement de recherche sur le nom et la description des Pads.

## Intégration avec le service etherpad distant

Le service etherpad distant stocke ses données dans PostgreSQL. La récupération des données des Pad dans l'application Pad (collaborativeeditor) s'opère par l'intermédiaire d'une API (`org.etherpad_lite_client`) fournie par la solution Etherpad.

L'éditeur Html est quant-à-lui intégré dans l'application Pad (collaborativeeditor) via une iframes.

## Modèle serveur

Le module serveur utilise un contrôleur et un helper.

Le contrôleur`CollaborativeEditorController`, qui correspond au point d'entrée de l'application, permet notamment l'établissement :
 * Du routage des vues, 
 * De la sécurité globale
 * De la déclaration des APIs de manipulation des Pad (`EtherpadHelper`)
 
 Le helper `EtherpadHelper` offre les méthodes pour communiquer avec l'api Etherpad par l’intermédiaire : 
 * De la déclaration de l'EPLiteClient (Api cliente native Etherpad)
 * De l'exposition de service de manipulation des Pads (création, suppression, liste et gestion des sessions)
 
Le contrôleur et le helper étendent les classes du framework Ent-core exploitant les CrudServices de base pour les métadonnées.

Le module serveur met en œuvre un évènement issu du framework Ent-core :

* `CollaborativeEditorSearchingEvents` : Logique de recherche

Un jsonschema permet de vérifier les données reçues par le serveur, il se trouve dans le dossier "src/main/resources/jsonschema".

## Modèle front-end

Le modèle Front-end manipule un objet model `CollaborativeEditors` correspondant aux pads.

Il y a une Collection globale `model.collaborativeeditors.all` qui contient l'ensemble des objets collaborativeeditor synchronisé depuis le serveur.
