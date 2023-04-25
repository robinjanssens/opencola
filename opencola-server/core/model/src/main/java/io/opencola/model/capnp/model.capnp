@0xb38c382d763e68ad;

using Java = import "java.capnp";
$Java.package("io.opencola.model.capnp");
$Java.outerClassname("Model");

struct Attribute {
  uri @0 :Text;
}

struct Value {
  bytes @0 :Data;
}

enum Operation {
  retract @0;
  add @1;
}

struct TransactionFact {
  attribute @0 :Attribute;
  value @1 :Value;
  operation @2 :Operation;
}

struct Id {
  bytes @0 :Data;
}

struct TransactionEntity {
  entityId @0 :Id;
  facts @1 :List(TransactionFact);
}

struct Transaction {
  id @0 :Id;
  authorityId @1 :Id;
  transactionEntities @2 :List(TransactionEntity);
  epochSecond @3 :UInt64;
}

struct Signature {
  algorithm @0 :Text;
  bytes @1 :Data;
}

struct SignedTransaction {
  transaction @0 :Transaction;
  signature @1 :Signature;
}