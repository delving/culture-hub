/*************************************************************************
	jquery.fancytree.table.js
	Table extension for jquery.fancytree.js.

	Copyright (c) 2013, Martin Wendt (http://wwWendt.de)
	Dual licensed under the MIT or GPL Version 2 licenses.
	http://code.google.com/p/fancytree/wiki/LicenseInfo

	A current version and some documentation is available at
		http://fancytree.googlecode.com/

	$Version:$
	$Revision:$

	@depends: jquery.js
	@depends: jquery.ui.widget.js
	@depends: jquery.ui.core.js
	@depends: jquery.fancytree.js
*************************************************************************/

// Start of local namespace
(function($) {
// relax some jslint checks:
/*globals alert */

"use strict";

// prevent duplicate loading
// if ( $.ui.fancytree && $.ui.fancytree.version ) {
//     $.ui.fancytree.warn("Fancytree: duplicate include");
//     return;
// }


/* *****************************************************************************
 * Private functions and variables
 */
function _assert(cond, msg){
	msg = msg || "";
	if(!cond){
		$.error("Assertion failed " + msg);
	}
}

function insertSiblingAfter(referenceNode, newNode) {
	referenceNode.parentNode.insertBefore(newNode, referenceNode.nextSibling);
}

/* Show/hide all rows that are structural descendants of `parent`. */
function setChildRowVisibility(parent, flag) {
	parent.visit(function(node){
		var tr = node.tr;
		if(tr){
			tr.style.display = flag ? "" : "none";
		}
		if(!node.expanded){
			return "skip";
		}
	});
}

/* Find node that is rendered in previous row. */
function findPrevRowNode(node){
	var parent = node.parent,
		siblings = parent ? parent.children : null,
		prev, i;

	if(siblings && siblings.length > 1 && siblings[0] !== node){
		// use the lowest descendant of the preceeding sibling
		i = $.inArray(node, siblings);
		prev = siblings[i - 1];
		_assert(prev.tr);
		// descend to lowest child (with a <tr> tag)
		while(prev.children){
			var last = prev.children[prev.children.length - 1];
			if(!last.tr){
				break;
			}
			prev = last;
		}
	}else{
		// if there is no preceding sibling, use the direct parent
		prev = parent;
	}
	return prev;
}


$.ui.fancytree.registerExtension("table", {
	// Default options for this extension.
	options: {
		indentation: 16,  // indent every node level by 16px
		nodeColumnIdx: 0  // render node expander, icon, and title to column #0
	},
	// Overide virtual methods for this extension.
	// `this`       : is this extension object
	// `this._super`: the virtual function that was overriden (member of prev. extension or Fancytree)
	treeInit: function(ctx){
		var tree = ctx.tree,
			$table = tree.widget.element;
		$table.addClass("fancytree-container fancytree-ext-table");
		tree.tbody = $table.find("> tbody")[0];
		tree.columnCount = $("thead >tr >th", $table).length;
		$(tree.tbody).empty();

		tree.rowFragment = document.createDocumentFragment();
		var $row = $("<tr>"),
			tdRole = "";
		if(ctx.options.aria){
			$row.attr("role", "row");
			tdRole = " role='gridcell'";
		}
		for(var i=0; i<tree.columnCount; i++) {
			if(ctx.options.table.nodeColumnIdx === i){
				$row.append("<td" + tdRole + "><span class='fancytree-node'></span></td>");
			}else{
				$row.append("<td" + tdRole + ">");
			}
		}
		tree.rowFragment.appendChild($row.get(0));

		this._super(ctx);
		// standard Fancytree created a root UL
		$(tree.rootNode.ul).remove();
		tree.rootNode.ul = null;
		tree.$container = $table;
		// Add container to the TAB chain
		tree.$container.attr("tabindex", "0");
		if(this.options.aria){
			tree.$container.attr("role", "treegrid");
			tree.$container.attr("aria-readonly", true);
		}
		// Make sure that status classes are set on the node's <tr> elements
		tree.statusClassPropName = "tr";
		tree.ariaPropName = "tr";
	},
	/* Called by nodeRender to sync node order with tag order.*/
//    nodeFixOrder: function(ctx) {
//    },
	nodeRemoveChildMarkup: function(ctx) {
		var node = ctx.node;
//		DT.debug("nodeRemoveChildMarkup()", node.toString());
		node.visit(function(n){
			if(n.tr){
				$(n.tr).remove();
				n.tr = null;
			}
		});
	},
	nodeRemoveMarkup: function(ctx) {
		var node = ctx.node;
//		DT.debug("nodeRemoveMarkup()", node.toString());
		if(node.tr){
			$(node.tr).remove();
			node.tr = null;
		}
		this.nodeRemoveChildMarkup(ctx);
	},
	/* Override standard render. */
	nodeRender: function(ctx, force, deep, collapsed, _recursive) {
		var tree = ctx.tree,
			node = ctx.node,
			opts = ctx.options,
			isRootNode = !node.parent;
//			firstTime = false;
		if( !_recursive ){
			ctx.hasCollapsedParents = node.parent && !node.parent.expanded;
		}
		if( !isRootNode ){
			if(!node.tr){
				// Create new <tr> after previous row
				var newRow = tree.rowFragment.firstChild.cloneNode(true),
					prevNode = findPrevRowNode(node);
//				firstTime = true;
//				$.ui.fancytree.debug("*** nodeRender " + node + ": prev: " + prevNode.key);
				_assert(prevNode);
				if(collapsed === true && _recursive){
					// hide all child rows, so we can use an animation to show it later
					newRow.style.display = "none";
				}else if(deep && ctx.hasCollapsedParents){
					// also hide this row if deep === true but any parent is collapsed
					newRow.style.display = "none";
//					newRow.style.color = "red";
				}
				if(!prevNode.tr){
					_assert(!prevNode.parent, "prev. row must have a tr, or is system root");
					tree.tbody.appendChild(newRow);
				}else{
					insertSiblingAfter(prevNode.tr, newRow);
				}
				node.tr = newRow;
				if( node.key && opts.generateIds ){
					node.tr.id = opts.idPrefix + node.key;
				}
				node.tr.ftnode = node;
				if(opts.aria){
					// TODO: why doesn't this work:
//                  node.li.role = "treeitem";
					$(node.tr).attr("aria-labelledby", "ftal_" + node.key);
				}
				node.span = $("span.fancytree-node", node.tr).get(0);
				// Set icon, link, and title (normally this is only required on initial render)
				this.nodeRenderTitle(ctx);
				// Allow tweaking, binding, after node was created for the first time
				tree._triggerNodeEvent("createnode", ctx);
			}
		}
		 // Allow tweaking after node state was rendered
		tree._triggerNodeEvent("rendernode", ctx);
		// Visit child nodes
		// Add child markup
		var children = node.children, i, l;
		if(children && (isRootNode || deep || node.expanded)){
			for(i=0, l=children.length; i<l; i++) {
				var subCtx = $.extend({}, ctx, {node: children[i]});
				subCtx.hasCollapsedParents = subCtx.hasCollapsedParents || !node.expanded;
				this.nodeRender(subCtx, force, deep, collapsed, true);
			}
		}
		// Make sure, that <tr> order matches node.children order.
		if(children && !_recursive){ // we only have to do it once, for the root branch
			var prevTr = node.tr || null,
				firstTr = tree.tbody.firstChild;
			// Iterate over all descendants
			node.visit(function(n){
				if(n.tr){
					if(!node.expanded && !isRootNode && n.tr.style.display !== "none"){
						// fix after a node was dropped over a sibling.
						// In this case it must be hidden
						n.tr.style.display = "none";
					}
					if(n.tr.previousSibling !== prevTr){
						node.debug("_fixOrder: mismatch at node: " + n);
						var nextTr = prevTr ? prevTr.nextSibling : firstTr;
						tree.tbody.insertBefore(n.tr, nextTr);
					}
					prevTr = n.tr;
				}
			});
		}
		// Update element classes according to node state
		if(!isRootNode){
			this.nodeRenderStatus(ctx);
		}
		// Finally add the whole structure to the DOM, so the browser can render
		// if(firstTime){
		//     parent.ul.appendChild(node.li);
		// }
			// TODO: just for debugging
	//            this._super(ctx);
	},
	nodeRenderTitle: function(ctx, title) {
		var node = ctx.node;
		this._super(ctx);
		// let user code write column content
		ctx.tree._triggerNodeEvent("rendercolumns", node);
	},
	 nodeRenderStatus: function(ctx) {
		 var node = ctx.node,
			 opts = ctx.options;
		 this._super(ctx);
		 // indent
		 var indent = (node.getLevel() - 1) * opts.table.indentation;
		 if(indent){
			 $(node.span).css({marginLeft: indent + "px"});
		 }
	 },
	/* Expand node, return Deferred.promise. */
	nodeSetExpanded: function(ctx, flag) {
		var node = ctx.node,
			dfd = new $.Deferred();
		this._super(ctx, flag).done(function(){
			flag = (flag !== false);
			setChildRowVisibility(ctx.node, flag);
			dfd.resolveWith(node);
		});
		return dfd;
	},
	nodeSetStatus: function(ctx, status, message, details) {
		if(status === "ok"){
			var node = ctx.node,
				firstChild = ( node.children ? node.children[0] : null );
			if ( firstChild && firstChild.isStatusNode ) {
				$(firstChild.tr).remove();
			}
		}
		this._super(ctx, status, message, details);
	}/*,
	treeSetFocus: function(ctx, flag) {
//	        alert("treeSetFocus" + ctx.tree.$container);
		ctx.tree.$container.focus();
		$.ui.fancytree.focusTree = ctx.tree;
	}*/
});
}(jQuery));
