var p = db.inventories.findOne();
print("productId value: " + p.productId);
print("productId ObjectId.equals string? " + (ObjectId.isValid(p.productId)));
var q = { productId: "6a86a7ee1a378fb7d75f6602" };
var foundByString = db.inventories.find(q).count();
var q2 = { productId: ObjectId("6a86a7ee1a378fb7d75f6602") };
var foundByObjectId = db.inventories.find(q2).count();
print("Found by String: " + foundByString);
print("Found by ObjectId: " + foundByObjectId);