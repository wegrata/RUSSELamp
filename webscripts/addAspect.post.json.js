var node = search.findNode(json.get("nodeRef"));

if (node==null) {
   status.code = 404;
   status.message = "Node " + json.get("nodeRef") + " not found.";
   status.redirect = true;
}

var aspects = json.get("aspects").split(",");
for (var aspectIndex=0;aspectIndex<aspects.length;aspectIndex++)
	node.addAspect(aspects[aspectIndex]);

model.node = node;
model.aspects = json.get("aspects");