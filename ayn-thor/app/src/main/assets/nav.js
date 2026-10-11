/**
 * ThorNav - gamepad spatial navigation for arbitrary web pages.
 *
 * Shared by the injected Showdown script (top screen) and the battle
 * panel (bottom screen). Moves a visible highlight between focusable
 * elements by direction, preferring elements that are on screen and not
 * covered by something else.
 */
(function (global) {
	'use strict';
	if (global.ThorNav) return;

	var FOCUS_CLASS = 'thor-focus';
	var DEFAULT_SELECTOR = [
		'a[href]', 'button', 'input:not([type=hidden])', 'select', 'textarea',
		'[tabindex]:not([tabindex="-1"])', '[role=button]', '[data-nav]',
	].join(',');

	function centerOf(r) {
		return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
	}

	function isUsable(el) {
		if (!el || !el.isConnected || el.disabled) return false;
		// Dialog boxes are often focusable themselves; only their contents are useful targets.
		if (el.matches('[role=dialog],[role=alertdialog],[role=document],dialog')) return false;
		if (el.closest('[aria-hidden=true],[inert]')) return false;
		var r = el.getBoundingClientRect();
		if (r.width < 2 || r.height < 2) return false;
		var cs = getComputedStyle(el);
		if (cs.visibility === 'hidden' || cs.display === 'none' || cs.opacity === '0') return false;
		return true;
	}

	function inViewport(r) {
		return r.bottom > 1 && r.right > 1 && r.top < innerHeight - 1 && r.left < innerWidth - 1;
	}

	/** True if the element's visible center isn't hidden under an overlay or clipped by a scroller. */
	function isHittable(el, r) {
		var x = Math.min(Math.max(r.left + Math.min(r.width / 2, 12), 1), innerWidth - 2);
		var y = Math.min(Math.max(r.top + r.height / 2, 1), innerHeight - 2);
		var hit = document.elementFromPoint(x, y);
		if (!hit) return true;
		return hit === el || el.contains(hit) || hit.contains(el) && hit.tagName === 'LABEL';
	}

	function overlapDistance(a1, a2, b1, b2) {
		if (a2 < b1) return b1 - a2;
		if (b2 < a1) return a1 - b2;
		return 0;
	}

	/** Lower is better; null means the candidate isn't in that direction. */
	function score(from, to, dir) {
		var fc = centerOf(from), tc = centerOf(to);
		var main, cross;
		switch (dir) {
		case 'right':
			if (tc.x <= fc.x + 1 || to.right <= from.right - 1) return null;
			main = Math.max(0, to.left - from.right);
			cross = overlapDistance(from.top, from.bottom, to.top, to.bottom);
			break;
		case 'left':
			if (tc.x >= fc.x - 1 || to.left >= from.left + 1) return null;
			main = Math.max(0, from.left - to.right);
			cross = overlapDistance(from.top, from.bottom, to.top, to.bottom);
			break;
		case 'down':
			if (tc.y <= fc.y + 1 || to.bottom <= from.bottom - 1) return null;
			main = Math.max(0, to.top - from.bottom);
			cross = overlapDistance(from.left, from.right, to.left, to.right);
			break;
		case 'up':
			if (tc.y >= fc.y - 1 || to.top >= from.top + 1) return null;
			main = Math.max(0, from.top - to.bottom);
			cross = overlapDistance(from.left, from.right, to.left, to.right);
			break;
		default:
			return null;
		}
		var align = (dir === 'left' || dir === 'right') ? Math.abs(tc.y - fc.y) : Math.abs(tc.x - fc.x);
		return main + cross * 4 + align * 0.15;
	}

	function create(opts) {
		opts = opts || {};
		var selector = opts.selector || DEFAULT_SELECTOR;
		var scope = opts.scope || function () { return document.body; };
		var exclude = opts.exclude || function () { return false; };
		var current = null;
		var lastRoot = null;
		var beforePopup = null;
		var lastPoint = { x: 0, y: 0 };
		var shown = false;

		function candidates() {
			var root = scope();
			if (!root) return [];
			var list = root.querySelectorAll(selector);
			var out = [];
			for (var i = 0; i < list.length; i++) {
				var el = list[i];
				if (!isUsable(el) || exclude(el)) continue;
				out.push(el);
			}
			return out;
		}

		function onscreen(list) {
			return list.filter(function (el) {
				var r = el.getBoundingClientRect();
				return inViewport(r) && isHittable(el, r);
			});
		}

		function setCurrent(el, scroll) {
			if (current && current !== el) current.classList.remove(FOCUS_CLASS);
			current = el;
			if (!el) return;
			if (shown) el.classList.add(FOCUS_CLASS);
			var r = el.getBoundingClientRect();
			if (scroll !== false && !inViewport(r)) {
				el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
				r = el.getBoundingClientRect();
			}
			lastPoint = centerOf(r);
			if (opts.onFocus) opts.onFocus(el);
		}

		function isValid(el) {
			if (!el || !isUsable(el) || exclude(el)) return false;
			var root = scope();
			return !!root && root.contains(el);
		}

		/** Pick the element closest to where the highlight was last seen. */
		function nearest(list) {
			var best = null, bestD = Infinity;
			for (var i = 0; i < list.length; i++) {
				var c = centerOf(list[i].getBoundingClientRect());
				var d = Math.hypot(c.x - lastPoint.x, c.y - lastPoint.y);
				if (d < bestD) { bestD = d; best = list[i]; }
			}
			return best;
		}

		function first(list) {
			var preferred = opts.preferred && opts.preferred();
			if (preferred && list.indexOf(preferred) >= 0) return preferred;
			// Text fields pop up the keyboard, so only start on one if nothing else is there.
			var plain = list.filter(function (el) { return !/^(INPUT|TEXTAREA)$/.test(el.tagName) || /^(checkbox|radio|button|submit)$/.test(el.type); });
			if (plain.length) list = plain;
			var best = null, bestS = Infinity;
			for (var i = 0; i < list.length; i++) {
				var r = list[i].getBoundingClientRect();
				var s = r.top * 2 + r.left;
				if (s < bestS) { bestS = s; best = list[i]; }
			}
			return best;
		}

		/** Make sure something sensible is highlighted; returns the current element. */
		function ensure() {
			if (isValid(current)) return current;
			var root = scope();
			var newScope = root !== lastRoot && root !== document.body;
			if (newScope && lastRoot === document.body) beforePopup = current;
			lastRoot = root;
			// Closing a popup returns to whatever opened it.
			if (root === document.body && beforePopup) {
				var back = beforePopup;
				beforePopup = null;
				if (isValid(back)) {
					setCurrent(back, true);
					return current;
				}
			}
			var list = onscreen(candidates());
			if (!list.length) list = candidates();
			// A new popup starts at its top; otherwise stay close to where the highlight was.
			setCurrent(!newScope && (lastPoint.x || lastPoint.y) ? nearest(list) : first(list), true);
			return current;
		}

		function move(dir) {
			show();
			if (!isValid(current)) { ensure(); return !!current; }
			var from = current.getBoundingClientRect();
			var all = candidates();
			var pick = function (list) {
				var best = null, bestS = Infinity;
				for (var i = 0; i < list.length; i++) {
					var el = list[i];
					if (el === current || el.contains(current) || current.contains(el)) continue;
					var s = score(from, el.getBoundingClientRect(), dir);
					if (s !== null && s < bestS) { bestS = s; best = el; }
				}
				return best;
			};
			var next = pick(onscreen(all)) || pick(all);
			if (next) {
				setCurrent(next, true);
				return true;
			}
			return false;
		}

		function show() {
			shown = true;
			if (current) current.classList.add(FOCUS_CLASS);
		}

		function hide() {
			shown = false;
			if (current) current.classList.remove(FOCUS_CLASS);
		}

		function focusFirst() {
			lastRoot = scope();
			var list = onscreen(candidates());
			if (!list.length) list = candidates();
			setCurrent(first(list), true);
			show();
		}

		return {
			move: move,
			ensure: ensure,
			show: show,
			hide: hide,
			focusFirst: focusFirst,
			set: function (el) { setCurrent(el, true); show(); },
			get current() { return isValid(current) ? current : null; },
			get shown() { return shown; },
		};
	}

	/** Nearest scrollable ancestor (or a fallback) of an element. */
	function scrollerFor(el, fallback) {
		for (var n = el; n && n !== document.body && n !== document.documentElement; n = n.parentElement) {
			var cs = getComputedStyle(n);
			if (/(auto|scroll)/.test(cs.overflowY) && n.scrollHeight > n.clientHeight + 4) return n;
		}
		return fallback || document.scrollingElement || document.documentElement;
	}

	global.ThorNav = { create: create, scrollerFor: scrollerFor, FOCUS_CLASS: FOCUS_CLASS, DEFAULT_SELECTOR: DEFAULT_SELECTOR };
})(window);
