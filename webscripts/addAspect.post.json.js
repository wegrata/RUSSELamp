/*
Copyright (c) 2012-2013, Eduworks Corporation. All rights reserved.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 3 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 
02110-1301 USA
*/
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