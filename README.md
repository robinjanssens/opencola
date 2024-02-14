# OpenCola

*Do you want to sell sugar water for the rest of your life, or do you want to come with me and change the world?*

Welcome to OpenCola!

If you are interested in a high level descripton of OpenCola, please visit our [homepage](https://opencola.io).


If you would like to understand the code bottom up, start in the [```model```](opencola-server/core/model/README.md). If you would like to start top down, start in [```Application.kt```](opencola-server/server/src/main/kotlin/opencola/server/Application.kt)

# Model

The data model for OpenCola is built on a few simple conepts:

## Entities

The "things" that OpenCola operates on are Entities. These entities are extendible, but in the context of the OpenCola application, the entities types are:

* ```Authority```: People that do things in the application. If the authority is one of your identities, it is called a persona. If it is someone you connected to, it is a peer. 
* ```Resource```: Something that is referenced via a url, for example a web page. 
* ```Post```: An entity that is created and available only within OpenCola (similar to a social media post)
* ```Comment```: An entity that holds a comment for another entity. It is a seprate entity so that it actitivy can be attached to comments, independent of the entity it refers to. 
* ```Data```: An arbitrary data blob that could be an image, a document, an arbitrary file, etc.


## Ids

Ids name entities in the OpenCola world. They are the result of applying a sha256 hash to some attribute of the object being identified, which allows ids to be determined in a consistent way regardless of location. For example, a blob of data is identified by its sha256 hash making it easy to look up in a content based data store or across users. Each entity has its own way to compute ids: 

* ```Authority```: The hash of the authority's public key.
* ```Resource```: The hash of the resource's url.
* ```Post```: The hash of a random UUID.
* ```Commnent```: The hash of a random UUID.
* ```Data```: The hash of the underlying binary data.

## Attributes

Data is attached to entities through attributes. Attributes have the following properties:

* ```name```: A short name for the attribute (e.g. ```description```) that is used as a proprety accessor on objects.
* ```uri```: A uri representing the full identifier for the attribute.
* ```type```: The type of attribute (can be one of ```SingleValue```, ```MultiValueSet``` or ```MultiValueList```)
* ```protoAttribute```: 
* ```valueWrapper```: A wrapper that knows how to serialize / de-serialize to / from a binary blob.
* ```isIndexable```: A boolean flag indicating whehter the attribute should be indexed for searching.
* ```computeFacts```: A function that can compute other facts (in order to fully understand this, you will need to first read about Facts below). This mechanism allows for back-pointers to be created between entities automatically. For example, a Comment entity has a ```parentId``` that points to its parent. In order for the parent to be able to point to the comment as a child, ```computeFacts``` will add create a fact that adds the comment id to the parents ```commentIds```.  

## Values

Values hold the actual data for an attribute. The current types support for values are:

* ```Boolean```
* ```ByteArray```
* ```Empty```
* ```Float```
* ```Id```
* ```Int```
* ```PublicKey```
* ```String```
* ```Uri```

Extending these types is straightforward.

## Facts

Facts are objects that tie together the above concepts and allow for a flexible data model that can be used and extended without an explicit schema (if you're familiar with [RDF](https://en.wikipedia.org/wiki/Resource_Description_Framework), facts can be thought of as extended RDF tripples) Each fact has the following properties:

*  ```authorityId``` 
*  ```entityId```
*  ```attribute```
*  ```value```
*  ```operation```
*  ```epochSecond```
*  ```transactionOrdinal```

Each "piece" of information about an entity (e.g. the name, url or text of a resource) is represented as a fact. Facts are generated whenever someone takes an action (creating a post, liking a post, etc.)

## Transactions

Transactions bundle facts together so they can be stored and as transmitted to peers. Each transaction has the following properties:

* ```id```: The id of the transaction, which is the hash of the previous (signed)transaction.  
* ```authorityId```: The id of the authority (person) whose activity is being. 
* ```transactionEntities```: A more effeicient representation of facts, where redundant authorityIds and entityIds are removed. 
* ```epochSecond```: The time at which the transaction occured. (This should probably be switched to epochMillisecond)

Transactions are then compressed (if they are in fact smaller after compression) and signed by the appropriate authority public key.

As transaction ids are "pointers" to the previous transaction, transaction chains are in fact (non crypto) block chains, that can be checked for integrity.

# Navigating the Code




The important parts of the code are: 

|Directory|Description|
|------|------|
|[```extension```](extension/README.md)| The chrome browser extension (HTML, JS, CSS)|
|[```install```](install/README.md)| Scripts for generating installers (Bash) |
|[```opencola-server```](opencola-server/README.md)| Backend code (oc, relay) (Kotlin) |
|[```web-ui```](web-ui/README.md)| The frontend for the application (Clojurescript)|







