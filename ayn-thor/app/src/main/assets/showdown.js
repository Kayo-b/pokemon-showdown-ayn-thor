/**
 * Injected into the Showdown client (top screen) at document start.
 *
 * - forces a desktop-sized viewport so the battle + chat layout fits
 * - exposes ThorPad.input(button) for gamepad navigation of the client
 * - exposes ThorPS.* so the bottom-screen panel can act on battles
 * - streams a compact battle snapshot to the app via ThorBridge
 *
 * Native code prepends `var THOR_CONFIG = {...};` before this file.
 */
(function () {
	'use strict';
	if (window.top !== window || window.ThorPS) return;
	var CONFIG = window.THOR_CONFIG || {};

	function post(msg) {
		try {
			window.ThorBridge.postMessage(JSON.stringify(msg));
		} catch (e) {}
	}
	function toID(text) {
		return ('' + (text || '')).toLowerCase().replace(/[^a-z0-9]+/g, '');
	}

	/* ---------- viewport ---------- */

	function fixViewport() {
		if (!CONFIG.width || !document.head) return;
		var content = 'width=' + CONFIG.width + ', user-scalable=no';
		var metas = document.head.querySelectorAll('meta[name=viewport]');
		if (!metas.length) {
			var m = document.createElement('meta');
			m.name = 'viewport';
			m.content = content;
			document.head.appendChild(m);
			return;
		}
		for (var i = 0; i < metas.length; i++) {
			if (metas[i].content !== content) metas[i].content = content;
		}
	}
	var viewportObserver = new MutationObserver(fixViewport);
	viewportObserver.observe(document, { childList: true, subtree: true });

	/* ---------- styles ---------- */

	var CSS = [
		'.thor-focus{outline:3px solid #ffd23f!important;outline-offset:1px!important;',
		'box-shadow:0 0 0 6px rgba(255,210,63,.45)!important;position:relative;z-index:1}',
		'html.thor-idle .thor-focus{outline-color:rgba(255,210,63,.35)!important;box-shadow:none!important}',
		// Battle view: only the current battle room is shown; popups, tooltips and the consent dialog stay.
		'html.thor-bv body>:not(.thor-cur):not(.ps-popup):not(.ps-overlay):not(#tooltipwrapper):not(.fc-consent-root):not(#header),',
		'html.thor-bv:not(.thor-ov) body>#header{display:none!important}',
		'html.thor-bv body{background:#000!important}',
		'html.thor-bv .thor-cur{left:0!important;right:0!important;top:0!important;bottom:0!important;',
		'width:auto!important;height:auto!important;background:#000!important}',
		'html.thor-bv .thor-cur>.battle{top:0!important;left:var(--thor-x)!important;margin:0!important;',
		'transform:scale(var(--thor-scale))!important;transform-origin:0 0!important}',
		'html.thor-bv .thor-cur>.foehint,html.thor-bv .thor-cur>.battle-userlist{display:none!important}',
		'html.thor-bv-full .thor-cur>:not(.battle){display:none!important}',
		// Overlay over the full arena: tabs on top, log and chat on the right, controls along the bottom.
		'html.thor-ov body>#header{z-index:20}',
		'html.thor-ov .thor-cur>.battle-log,html.thor-ov .thor-cur>.battle-log-add,',
		'html.thor-ov .thor-cur>.battle-controls{display:block!important;z-index:10;',
		'background:rgba(244,246,250,.94)!important;box-shadow:0 0 18px rgba(0,0,0,.5)}',
		'html.thor-ov .thor-cur>.battle-log{left:auto!important;right:0!important;top:50px!important;',
		'bottom:40px!important;width:340px!important;height:auto!important}',
		'html.thor-ov .thor-cur>.battle-log-add{left:auto!important;right:0!important;top:auto!important;',
		'bottom:0!important;width:340px!important}',
		'html.thor-ov .thor-cur>.battle-controls{left:0!important;right:340px!important;top:auto!important;',
		'bottom:0!important;width:auto!important;height:auto!important;max-height:45%;overflow:auto!important}',
		'html.thor-bv-log .thor-cur>.battle-log{left:var(--thor-w)!important;right:0!important;top:0!important;',
		'bottom:40px!important;width:auto!important;height:auto!important}',
		'html.thor-bv-log .thor-cur>.battle-log-add{left:var(--thor-w)!important;right:0!important;',
		'bottom:0!important;top:auto!important;width:auto!important}',
		'html.thor-bv-log .thor-cur>.battle-controls{left:0!important;top:var(--thor-h)!important;',
		'width:var(--thor-w)!important;bottom:0!important;height:auto!important;overflow:auto!important}',
	].join('');

	/* ---------- battle view ---------- */

	var view = 'classic';
	var overlay = false;
	function updateView() {
		var room = curRoom();
		var el = view !== 'classic' && room && room.battle && room.el || null;
		var old = document.querySelector('.thor-cur');
		if (old && old !== el) {
			old.classList.remove('thor-cur');
			overlay = false; // each battle starts with the plain arena
		}
		var root = document.documentElement;
		root.classList.toggle('thor-bv', !!el);
		root.classList.toggle('thor-bv-full', !!el && view === 'full');
		root.classList.toggle('thor-bv-log', !!el && view === 'log');
		root.classList.toggle('thor-ov', !!el && view === 'full' && overlay);
		if (!el) return;
		el.classList.add('thor-cur');
		// Scale the 640x360 arena to fill the screen, leaving a log column in 'log' view.
		var w = innerWidth, h = innerHeight;
		var scale = view === 'full' ? Math.min(w / 640, h / 360) : Math.min(w * 0.75 / 640, h / 360);
		var x = view === 'full' ? Math.max(0, (w - 640 * scale) / 2) : 0;
		root.style.setProperty('--thor-scale', scale.toFixed(4));
		root.style.setProperty('--thor-x', x + 'px');
		root.style.setProperty('--thor-w', Math.round(640 * scale) + 'px');
		root.style.setProperty('--thor-h', Math.round(360 * scale) + 'px');
	}
	setInterval(updateView, 250);

	function overlayAvailable() {
		var room = curRoom();
		return view === 'full' && !!(room && room.battle && room.el);
	}

	/** Shows or hides the overlay; returns false when there is no full-screen battle to overlay. */
	function setOverlay(on) {
		if (!overlayAvailable()) return false;
		overlay = on;
		updateView();
		if (on) {
			var log = curRoom().el.querySelector('.battle-log');
			if (log) log.scrollTop = log.scrollHeight;
		}
		schedule();
		return true;
	}

	function onReady() {
		viewportObserver.disconnect();
		fixViewport();
		// The client resets the viewport on resize; put ours back whenever it does.
		var metas = document.head.querySelectorAll('meta[name=viewport]');
		for (var i = 0; i < metas.length; i++) {
			new MutationObserver(fixViewport).observe(metas[i], { attributes: true, attributeFilter: ['content'] });
		}
		var style = document.createElement('style');
		style.id = 'thor-style';
		style.textContent = CSS;
		document.head.appendChild(style);
		post({ t: 'ready' });
	}
	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', onReady);
	} else {
		onReady();
	}

	/* ---------- gamepad navigation ---------- */

	function isShown(el) {
		return el && el.isConnected && el.getClientRects().length > 0 &&
			getComputedStyle(el).visibility !== 'hidden';
	}

	// Showdown's own popups, plus third-party modals such as the cookie consent dialog.
	var POPUP_SELECTOR = '.ps-popup, .fc-consent-root, [aria-modal=true], dialog[open]';

	function topPopup() {
		var popups = document.querySelectorAll(POPUP_SELECTOR);
		for (var i = popups.length - 1; i >= 0; i--) {
			if (isShown(popups[i])) return popups[i];
		}
		return null;
	}

	function curRoom() {
		return window.app && app.curRoom || null;
	}

	var nav = null;
	function getNav() {
		if (nav || !window.ThorNav) return nav;
		nav = ThorNav.create({
			// Teambuilder search results are links without an href.
			selector: ThorNav.DEFAULT_SELECTOR + ',a[data-entry]',
			scope: function () { return topPopup() || document.body; },
			onFocus: function (el) {
				// Hovering a teambuilder result shows its details on the bottom screen.
				if (!el.hasAttribute('data-entry')) return;
				var data = null;
				try { data = entryPreview(el.getAttribute('data-entry')); } catch (e) {}
				if (data) post({ t: 'preview', data: data });
			},
			preferred: function () {
				var room = curRoom();
				if (!room || !room.el || topPopup()) return null;
				return room.el.querySelector('.movebutton:not([disabled]), button[name=chooseSwitch], button[name=chooseTeamPreview]');
			},
		});
		return nav;
	}

	function isText(el) {
		if (!el) return false;
		if (el.tagName === 'TEXTAREA') return true;
		if (el.isContentEditable) return true;
		return el.tagName === 'INPUT' && /^(text|search|password|email|number|url|tel|)$/i.test(el.type || '');
	}

	function moveCaret(el, delta) {
		try {
			var p = Math.max(0, Math.min(el.value.length, (el.selectionStart || 0) + delta));
			el.setSelectionRange(p, p);
		} catch (e) {}
	}

	function openText(el) {
		el.focus();
		post({ t: 'ime', show: true });
	}

	function activate(el) {
		if (!el) return;
		if (isText(el)) {
			// Teambuilder fields: the first press only selects the field so its result list shows
			// below; pressing again opens the keyboard to type a search.
			if (el.classList.contains('chartinput') && document.activeElement !== el) {
				el.focus();
				post({ t: 'ime', show: false });
				return;
			}
			openText(el);
			return;
		}
		if (el.tagName === 'SELECT') {
			el.focus();
			try {
				el.showPicker();
			} catch (e) {
				el.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
			}
			return;
		}
		try { el.focus({ preventScroll: true }); } catch (e) {}
		el.click();
	}

	/** The log or list the right stick should scroll in the current room. */
	function roomScroller() {
		var popup = topPopup();
		if (popup) return ThorNav.scrollerFor(popup.firstElementChild || popup, popup);
		var room = curRoom();
		if (room && room.el) {
			var log = room.el.querySelector('.battle-log, .chat-log, .ladder, .teambuilder-results, .teamwrapper, .mainmenuwrapper');
			if (log && isShown(log)) return log;
			return ThorNav.scrollerFor(room.el.firstElementChild || room.el, room.el);
		}
		return document.scrollingElement;
	}

	function scrollBy(dx, dy) {
		var target = roomScroller();
		if (target) target.scrollBy(dx, dy);
	}

	function back() {
		var ae = document.activeElement;
		if (isText(ae)) {
			ae.blur();
			post({ t: 'ime', show: false });
			return;
		}
		if (window.BattleTooltips && BattleTooltips.hideTooltip) {
			try { BattleTooltips.hideTooltip(); } catch (e) {}
		}
		if (window.app && app.popups && app.popups.length) {
			app.closePopup();
			return;
		}
		var popup = topPopup();
		if (popup) {
			var close = popup.querySelector('button[name=close], .closebutton');
			if (close) close.click();
			return;
		}
		if (overlay && setOverlay(false)) return;
		var room = curRoom();
		// Inside a room with a "back" style control (e.g. the battle move menu), use it.
		if (room && room.el) {
			var cancel = room.el.querySelector('button[name=clearChoice]:not([disabled]), button[name=closeTeamBuilder]');
			if (cancel && isShown(cancel)) {
				cancel.click();
				return;
			}
		}
		post({ t: 'backUnhandled' });
	}

	function focusChat() {
		var room = curRoom();
		var box = null;
		if (room) {
			if (room.$chatbox && room.$chatbox[0]) box = room.$chatbox[0];
			else if (room.el) box = room.el.querySelector('textarea.textbox, input.textbox');
		}
		if (!box || !isShown(box)) box = document.querySelector('.ps-room:not([style*="none"]) textarea.textbox');
		if (box) {
			var n = getNav();
			if (n) n.set(box);
			openText(box);
		}
	}

	function cycleRoom(delta) {
		if (window.app && app.focusRoomBy && app.curRoom) {
			try {
				app.focusRoomBy(app.curRoom, delta);
				return;
			} catch (e) {}
		}
		var tabs = Array.prototype.slice.call(document.querySelectorAll('.tabbar a.button, .tabbar button.button'));
		var cur = tabs.findIndex(function (t) { return t.classList.contains('cur'); });
		var next = tabs[(cur + delta + tabs.length) % tabs.length];
		if (next) next.click();
	}

	var ThorPad = {
		input: function (button) {
			var n = getNav();
			if (!n) return;
			document.documentElement.classList.remove('thor-idle');
			var ae = document.activeElement;
			switch (button) {
			case 'up': case 'down': case 'left': case 'right':
				if (isText(ae)) {
					if (button === 'left' || button === 'right') {
						moveCaret(ae, button === 'left' ? -1 : 1);
						return;
					}
					ae.blur();
					post({ t: 'ime', show: false });
				}
				if (!n.move(button)) {
					var step = 160;
					scrollBy(button === 'left' ? -step : button === 'right' ? step : 0,
						button === 'up' ? -step : button === 'down' ? step : 0);
				}
				break;
			case 'a':
				if (!n.shown || !n.current) {
					n.ensure();
					n.show();
					return;
				}
				activate(n.current);
				break;
			case 'b':
				back();
				break;
			case 'x':
				// Over the full arena, the first press shows the overlay; the next one opens chat.
				if (!(!overlay && setOverlay(true))) focusChat();
				break;
			case 'y':
				if (window.app && app.focusRoom) app.focusRoom('');
				break;
			case 'l1':
				cycleRoom(-1);
				break;
			case 'r1':
				cycleRoom(1);
				break;
			case 'l2': case 'r2': {
				var s = roomScroller();
				if (s) s.scrollBy(0, (button === 'l2' ? -1 : 1) * s.clientHeight * 0.8);
				break;
			}
			}
			// Actions often replace the highlighted element (popups closing, rooms switching).
			setTimeout(function () {
				if (n.shown && !n.current) n.ensure();
			}, 250);
		},
		scroll: function (dx, dy) {
			var s = roomScroller();
			if (s) s.scrollBy(dx, dy);
			else scrollBy(dx, dy);
		},
		toggleOverlay: function () {
			setOverlay(!overlay);
		},
		/** Called when control moves to the bottom screen and back. */
		setActive: function (active) {
			document.documentElement.classList.toggle('thor-idle', !active);
		},
		/** 'full' (arena only), 'log' (arena and battle log) or 'classic'. */
		setView: function (v) {
			view = v;
			updateView();
		},
	};
	window.ThorPad = ThorPad;

	// Touch input hides the gamepad highlight until the pad is used again.
	window.addEventListener('pointerdown', function (e) {
		if (e.pointerType === 'touch' && nav) nav.hide();
	}, true);

	/* ---------- battle state for the panel ---------- */

	var results = {}; // roomid -> {winner, tie}
	var errors = {}; // roomid -> {text, rqid}

	function sniff(data) {
		if (typeof data !== 'string') return;
		var roomid = 'lobby';
		if (data.charAt(0) === '>') {
			var nl = data.indexOf('\n');
			roomid = data.slice(1, nl < 0 ? undefined : nl);
		}
		if (!roomid.startsWith('battle-')) return;
		var lines = data.split('\n');
		for (var i = 0; i < lines.length; i++) {
			var line = lines[i];
			if (line.startsWith('|win|')) {
				results[roomid] = { winner: line.slice(5) };
			} else if (line === '|tie' || line.startsWith('|tie|')) {
				results[roomid] = { tie: true };
			} else if (/^\|error\|\[(Invalid|Unavailable) choice\]/.test(line)) {
				var room = window.app && app.rooms[roomid];
				errors[roomid] = { text: line.slice(7), rqid: room && room.request ? room.request.rqid : 0 };
			}
		}
	}

	function hookApp() {
		if (!window.app || typeof app.receive !== 'function') return false;
		if (app.receive.__thor) return true;
		var orig = app.receive;
		var wrapped = function (data) {
			var ret = orig.apply(this, arguments);
			try { sniff(data); } catch (e) {}
			schedule();
			return ret;
		};
		wrapped.__thor = true;
		app.receive = wrapped;
		return true;
	}

	var typeCache = {};
	function speciesTypes(species, gen) {
		var key = gen + ':' + species;
		if (typeCache[key]) return typeCache[key];
		var types = [];
		try { types = Dex.forGen(gen).species.get(species).types || []; } catch (e) {}
		typeCache[key] = types;
		return types;
	}

	function effectiveness(moveType, defTypes) {
		var chart = window.BattleTypeChart;
		if (!chart || !moveType || !defTypes || !defTypes.length) return null;
		var mult = 1;
		for (var i = 0; i < defTypes.length; i++) {
			var entry = chart[toID(defTypes[i])];
			if (!entry || !entry.damageTaken) continue;
			var d = entry.damageTaken[moveType];
			if (d === 1) mult *= 2;
			else if (d === 2) mult *= 0.5;
			else if (d === 3) mult *= 0;
		}
		return mult;
	}

	/** The panel is a local page, so protocol-relative URLs must be made absolute. */
	function absolute(text) {
		return ('' + (text || '')).replace(/url\(\/\//g, 'url(https://').replace(/^\/\//, 'https://');
	}

	/* ---------- teambuilder previews ---------- */

	/** Details for a teambuilder search result ("pokemon|Name", "item|Name", ...), or null. */
	function entryPreview(entry) {
		var bar = entry.indexOf('|');
		var kind = entry.slice(0, bar), name = entry.slice(bar + 1);
		var room = curRoom();
		var m = /^gen(\d+)/.exec(room && room.curTeam && room.curTeam.format || '');
		var gen = m ? +m[1] : 9;
		var dex = Dex.forGen ? Dex.forGen(gen) : Dex;
		var text = function (effect) {
			var t = dex.text ? dex.text.get(effect) : effect;
			return t.desc || t.shortDesc || '';
		};
		if (kind === 'pokemon') {
			var sp = dex.species.get(name);
			if (!sp || !sp.exists) return null;
			var sprite = '';
			try { sprite = absolute(Dex.getSpriteData(sp, true, { gen: Math.max(gen, 5) }).url); } catch (e) {}
			var abilities = [];
			var labels = { 0: '', 1: '', H: 'Hidden', S: 'Special' };
			for (var slot in sp.abilities) {
				var ab = dex.abilities.get(sp.abilities[slot]);
				var at = dex.text ? dex.text.get(ab) : ab;
				abilities.push({ name: ab.name, slot: labels[slot] || '', desc: at.shortDesc || at.desc || '' });
			}
			return {
				kind: 'pokemon', name: sp.name, types: sp.types, tier: sp.tier || '',
				stats: sp.baseStats, abilities: abilities, sprite: sprite, icon: icon(sp.name),
			};
		}
		if (kind === 'item') {
			var it = dex.items.get(name);
			if (!it || !it.exists) return null;
			var itemIcon = '';
			try { itemIcon = absolute(Dex.getItemIcon(it)); } catch (e) {}
			return { kind: 'item', name: it.name, icon: itemIcon, desc: text(it) };
		}
		if (kind === 'move') {
			var mv = dex.moves.get(name);
			if (!mv || !mv.exists) return null;
			return {
				kind: 'move', name: mv.name, type: mv.type, category: mv.category,
				bp: mv.basePower || 0, accuracy: mv.accuracy === true ? 0 : mv.accuracy,
				pp: mv.pp || 0, priority: mv.priority || 0, desc: text(mv),
			};
		}
		if (kind === 'ability') {
			var a = dex.abilities.get(name);
			if (!a || !a.exists) return null;
			return { kind: 'ability', name: a.name, desc: text(a) };
		}
		return null;
	}

	function icon(pokemon, facingLeft) {
		try { return absolute(Dex.getPokemonIcon(pokemon, facingLeft)); } catch (e) { return ''; }
	}

	function battlePokemon(p, gen, front) {
		if (!p) return null;
		var types = [];
		try { types = p.getTypeList(); } catch (e) { types = speciesTypes(p.speciesForme, gen); }
		return {
			name: p.name,
			species: p.speciesForme,
			level: p.level,
			hp: p.hp,
			maxhp: p.maxhp,
			status: p.status || '',
			fainted: !!p.fainted || p.hp === 0 && p.maxhp > 0,
			tera: p.terastallized || '',
			types: types,
			icon: icon(p, !front),
		};
	}

	function sideInfo(side, gen, front) {
		if (!side) return null;
		return {
			id: side.sideid,
			name: side.name,
			active: (side.active || []).map(function (p) { return battlePokemon(p, gen, front); }),
			team: (side.pokemon || []).map(function (p) { return battlePokemon(p, gen, front); }),
			total: side.totalPokemon || 6,
		};
	}

	function itemName(dex, id) {
		if (!id) return '';
		try { return dex.items.get(id).name || id; } catch (e) { return id; }
	}

	function requestInfo(room, b, foe) {
		var req = room.request;
		if (!req) return null;
		var gen = b.gen || 9;
		var dex;
		try { dex = Dex.forGen(gen); } catch (e) { dex = window.Dex; }
		var foeTypes = (foe && foe.active || []).map(function (p) { return p && !p.fainted ? p.types : null; });
		var my = b.myPokemon || [];
		var team = (req.side && req.side.pokemon || []).map(function (sp, i) {
			var details = sp.details || '';
			var species = details.split(',')[0];
			var cond = (sp.condition || '').split(' ');
			var hp = cond[0].split('/');
			return {
				ident: sp.ident,
				name: sp.ident ? sp.ident.slice(sp.ident.indexOf(':') + 2) : species,
				species: species,
				level: (/, L(\d+)/.exec(details) || [0, 100])[1] | 0,
				hp: parseInt(hp[0], 10) || 0,
				maxhp: parseInt(hp[1], 10) || 0,
				status: cond[1] === 'fnt' ? '' : cond[1] || '',
				fainted: cond[1] === 'fnt',
				active: !!sp.active,
				item: itemName(dex, sp.item),
				ability: sp.ability || sp.baseAbility || '',
				tera: sp.terastallized || '',
				teraType: sp.teraType || '',
				reviving: !!sp.reviving,
				commanding: !!sp.commanding,
				types: sp.terastallized ? [sp.terastallized] : speciesTypes(species, gen),
				icon: icon(my[i] && my[i].speciesForme ? my[i] : species),
			};
		});
		var active = (req.active || []).map(function (a) {
			var describe = function (m, i) {
				var md = {};
				try { md = dex.moves.get(m.id || m.move) || {}; } catch (e) {}
				var type = md.type || '';
				var cat = md.category || '';
				var bp = md.basePower || 0;
				return {
					index: i + 1,
					name: m.move,
					id: m.id,
					pp: m.pp,
					maxpp: m.maxpp,
					target: m.target || md.target || 'normal',
					disabled: !!m.disabled,
					type: type,
					category: cat,
					bp: bp,
					accuracy: md.accuracy === true ? '—' : md.accuracy || '',
					desc: md.shortDesc || md.desc || '',
					eff: cat === 'Status' ? [] : foeTypes.map(function (t) { return t ? effectiveness(type, t) : null; }),
				};
			};
			var out = {
				moves: (a.moves || []).map(describe),
				trapped: !!a.trapped,
				maybeTrapped: !!a.maybeTrapped,
				maybeDisabled: !!a.maybeDisabled,
				canMegaEvo: !!a.canMegaEvo,
				canMegaEvoX: !!a.canMegaEvoX,
				canMegaEvoY: !!a.canMegaEvoY,
				canUltraBurst: !!a.canUltraBurst,
				canDynamax: !!a.canDynamax,
				canTerastallize: a.canTerastallize || '',
				zMoves: null,
				maxMoves: null,
			};
			if (a.canZMove) {
				out.zMoves = a.canZMove.map(function (z, i) {
					return z ? describe({ move: z.move, id: toID(z.move), target: z.target }, i) : null;
				});
			}
			if (a.maxMoves && a.maxMoves.maxMoves) {
				out.maxMoves = a.maxMoves.maxMoves.map(function (mm, i) {
					var d = describe({ move: mm.move, id: toID(mm.move), target: mm.target }, i);
					d.disabled = !!mm.disabled;
					return d;
				});
				out.gigantamax = a.maxMoves.gigantamax || '';
			}
			return out;
		});
		var kind = req.wait ? 'wait' : req.teamPreview ? 'team' : req.forceSwitch ? 'switch' : 'move';
		return {
			kind: kind,
			rqid: req.rqid,
			forceSwitch: req.forceSwitch || null,
			noCancel: !!req.noCancel,
			maxChosenTeamSize: req.maxChosenTeamSize || 0,
			sideId: req.side ? req.side.id : '',
			team: team,
			active: active,
		};
	}

	function battleInfo(room) {
		var b = room.battle;
		var gen = b.gen || 9;
		var mine = !!(room.request && room.request.side);
		var foe = sideInfo(mine ? b.farSide : b.sides && b.sides[1], gen, true);
		var me = sideInfo(mine ? b.mySide : b.sides && b.sides[0], gen, false);
		var err = errors[room.id];
		var req = requestInfo(room, b, foe);
		if (err && req && err.rqid !== req.rqid) {
			delete errors[room.id];
			err = null;
		}
		return {
			id: room.id,
			title: room.title || room.id,
			gen: gen,
			gameType: b.gameType || 'singles',
			turn: b.turn || 0,
			ended: !!b.ended,
			result: results[room.id] || null,
			mine: mine,
			me: me,
			foe: foe,
			request: req,
			waiting: !!(room.choice && room.choice.waiting) || !!(req && req.kind === 'wait'),
			timer: !!b.kickingInactive,
			error: err ? err.text : '',
		};
	}

	function collect() {
		if (!window.app || !app.rooms) return null;
		var battles = [];
		var ids = (app.roomList || []).concat(app.sideRoomList || []).map(function (r) { return r.id; });
		for (var id in app.rooms) {
			if (ids.indexOf(id) < 0) ids.push(id);
		}
		for (var i = 0; i < ids.length; i++) {
			var room = app.rooms[ids[i]];
			if (!room || !room.battle) continue;
			try {
				battles.push(battleInfo(room));
			} catch (e) {
				battles.push({ id: room.id, title: room.title || room.id, broken: '' + e });
			}
		}
		var user = app.user;
		return {
			t: 'state',
			cur: app.curRoom ? app.curRoom.id : '',
			user: user ? user.get('name') : '',
			named: user ? !!user.get('named') : false,
			popup: !!(app.popups && app.popups.length),
			overlay: overlayAvailable() ? overlay : null,
			battles: battles,
		};
	}

	var lastJSON = '';
	var timer = 0;
	function flush() {
		timer = 0;
		hookApp();
		var state = collect();
		if (!state) return;
		var json = JSON.stringify(state);
		if (json === lastJSON) return;
		lastJSON = json;
		try {
			window.ThorBridge.postMessage(json);
		} catch (e) {}
	}
	function schedule() {
		if (!timer) timer = setTimeout(flush, 80);
	}
	setInterval(schedule, 600);

	/* ---------- commands from the panel ---------- */

	function room(id) {
		return window.app && app.rooms && app.rooms[id] || null;
	}

	window.ThorPS = {
		choose: function (roomid, choice, rqid) {
			var r = room(roomid);
			if (!r) return;
			var id = rqid || r.request && r.request.rqid;
			r.send('/choose ' + choice + (id ? '|' + id : ''));
			// Keep the client's own controls in sync with the choice made on the panel.
			try {
				r.choice = { waiting: true };
				r.updateControlsForPlayer();
			} catch (e) {}
			schedule();
		},
		undo: function (roomid) {
			var r = room(roomid);
			if (!r) return;
			if (r.undoChoice) {
				r.undoChoice();
			} else {
				r.send('/undo');
			}
			schedule();
		},
		/** Drop a rejected choice so the client and panel can choose again. */
		reset: function (roomid) {
			var r = room(roomid);
			delete errors[roomid];
			if (!r) return;
			try {
				r.choice = null;
				r.updateControlsForPlayer();
			} catch (e) {}
			schedule();
		},
		cmd: function (roomid, text) {
			var r = room(roomid);
			if (r) r.send(text);
			else if (window.app) app.send(text);
			schedule();
		},
		focus: function (roomid) {
			if (window.app && app.focusRoom && room(roomid)) app.focusRoom(roomid);
			schedule();
		},
		leave: function (roomid) {
			if (window.app && app.leaveRoom) app.leaveRoom(roomid);
			schedule();
		},
		refresh: function () {
			lastJSON = '';
			schedule();
		},
	};
})();
