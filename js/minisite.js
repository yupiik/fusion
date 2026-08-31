// forked from anchorifix to adjust top padding - license MIT
if (typeof Object.create !== 'function') {
  Object.create = function (obj) {
    function F() {}
    F.prototype = obj;
    return new F();
  };
}

(function ($, window, document, undefined) {
  'use strict';

  var generatedNavMenu = {
    init: function (options, elem) {
      var self = this;

      self.elem = elem;
      self.$elem = $(elem);

      self.opt = $.extend({}, this.opt, options);

      self.headers = self.$elem.find(self.opt.headers);
      self.previous = 0;

      // Fix bug #1
      if (self.headers.length !== 0) {
        self.first = parseInt(self.headers.prop('nodeName').substring(1), null);
      } else {
        $('.page-navigation-right').children().hide();
      }

      self.build();

      for (var i = 0; i < self.opt.navElements.length; i++) {
        var nav = self.opt.navElements[i];
        $(self.opt.navigation).children().clone(true).appendTo(nav);
      }
    },

    opt: {
      navigation: '.generated-nav-menu', // position of navigation
      headers: 'h1, h2, h3, h4, h5, h6', // custom headers selector
      speed: 200, // speed of sliding back to top
      anchorClass: 'anchor', // class of anchor links
      anchorText: '#', // prepended or appended to anchor headings
      top: '.top', // back to top button or link class
      spy: true, // scroll spy
      position: 'append', // position of anchor text
      spyOffset: !0, // specify heading offset for spy scrolling
      navElements: [], // if there are other elements that should act as navigation, add classes here
    },

    build: function () {
      var self = this,
        obj,
        navigations = function () {};
      // when navigation configuration is set
      if (self.opt.navigation) {
        $(self.opt.navigation).append('<ul />');
        self.previous = $(self.opt.navigation).find('ul').last();
        navigations = function (obj) {
          return self.navigations(obj);
        };
      }

      for (var i = 0; i < self.headers.length; i++) {
        obj = self.headers.eq(i);
        navigations(obj);
        self.anchor(obj);
      }

      if (self.opt.spy) self.spy();

      if (self.opt.top) self.back();
    },

    navigations: function (obj) {
      var self = this,
        link,
        list,
        which,
        name = self.name(obj);

      if (obj.attr('id') !== undefined) name = obj.attr('id');

      link = $('<a />')
        .attr('href', '#' + name)
        .text(obj.text());
      list = $('<li />').append(link);

      which = parseInt(obj.prop('nodeName').substring(1), null);
      list.attr('data-tag', which);

      self.subheadings(which, list);

      self.first = which;
    },

    subheadings: function (which, a) {
      var self = this,
        ul = $(self.opt.navigation).find('ul'),
        li = $(self.opt.navigation).find('li');

      if (which === self.first) {
        self.previous.append(a);
      } else if (which > self.first) {
        li.last().append('<ul />');
        // can't use cache ul; need to find ul once more
        $(self.opt.navigation).find('ul').last().append(a);
        self.previous = a.parent();
      } else {
        $('li[data-tag=' + which + ']')
          .last()
          .parent()
          .append(a);
        self.previous = a.parent();
      }
    },

    name: function (obj) {
      var name = obj
        .text()
        .replace(/[^\w\s]/gi, '')
        .replace(/\s+/g, '-')
        .toLowerCase();

      return name;
    },

    anchor: function (obj) {
      var self = this,
        name = self.name(obj),
        anchor,
        text = self.opt.anchorText,
        klass = self.opt.anchorClass,
        id;

      if (obj.attr('id') === undefined) obj.attr('id', name);

      id = obj.attr('id');

      anchor = $('<a />')
        .attr('href', '#' + id)
        .html(text)
        .addClass(klass);

      if (self.opt.position === 'append') {
        obj.append(anchor);
      } else {
        obj.prepend(anchor);
      }
    },

    back: function () {
      var self = this,
        body = $('body, html'),
        top = $(self.opt.top);

      top.on('click', function (e) {
        e.preventDefault();

        body.animate(
          {
            scrollTop: 0,
          },
          self.opt.speed
        );
      });
    },

    top: function (that) {
      var self = this,
        top = self.opt.top,
        back;

      if (top !== false) {
        back = $(that).scrollTop() > 200 ? $(top).fadeIn() : $(top).fadeOut();
      }
    },

    spy: function () {
      var self = this,
        previous,
        current,
        list,
        top,
        prev;

      $(window).scroll(function (e) {
        // show links back to top
        self.top(this);
        // get all the header on top of the viewport
        current = self.headers.map(function (e) {
          if (
            $(this).offset().top - $(window).scrollTop() <
            self.opt.spyOffset
          ) {
            return this;
          }
        });
        // get only the latest header on the viewport
        current = $(current).eq(current.length - 1);

        if (current && current.length) {
          // get all li tag that contains href of # ( all the parents )
          list = $('li:has(a[href="#' + current.attr('id') + '"])');

          if (prev !== undefined) {
            prev.removeClass('active');
          }

          list.addClass('active');
          prev = list;
        }
      });
    },
  };

  $.fn.generatedNavMenu = function (options) {
    return this.each(function () {
      if (!$.data(this, 'generated-nav-menu')) {
        var anchor = Object.create(generatedNavMenu);
        anchor.init(options, this);
        $.data(this, 'generated-nav-menu', anchor);
      }
    });
  };
})(jQuery, window, document);


// ---------------------------------------------------------------------------
// search with inverted index
// ---------------------------------------------------------------------------

// Porter stemmer in JS (Porter Stemming Algorithm)
var PorterStemmer = {
  isConsonant: function (word, i) {
    var ch = word.charAt(i);
    switch (ch) {
      case 'a': case 'e': case 'i': case 'o': case 'u':
        return false;
      case 'y':
        return i === 0 || !this.isConsonant(word, i - 1);
      default:
        return true;
    }
  },

  measure: function (word) {
    var m = 0, i = 0, n = word.length, foundVc = false;
    while (i < n) {
      if (!this.isConsonant(word, i)) {
        i++;
        while (i < n && !this.isConsonant(word, i)) { i++; }
        if (i < n) foundVc = true;
      } else {
        if (foundVc) { m++; foundVc = false; }
        i++;
        while (i < n && this.isConsonant(word, i)) { i++; }
      }
    }
    return m;
  },

  containsVowel: function (word) {
    for (var i = 0; i < word.length; i++) {
      if (!this.isConsonant(word, i)) return true;
    }
    return false;
  },

  doubleConsonant: function (word) {
    var n = word.length;
    return n >= 2 && this.isConsonant(word, n - 1) && word.charAt(n - 1) === word.charAt(n - 2);
  },

  cvc: function (word) {
    var n = word.length;
    if (n < 3) return false;
    if (this.isConsonant(word, n - 3) && !this.isConsonant(word, n - 2) && this.isConsonant(word, n - 1)) {
      var last = word.charAt(n - 1);
      return last !== 'w' && last !== 'x' && last !== 'y';
    }
    return false;
  },

  replaceSuffix: function (word, suffix, replacement) {
    if (word.indexOf(suffix, word.length - suffix.length) === -1) return word;
    return word.substring(0, word.length - suffix.length) + replacement;
  },

  step1a: function (word) {
    if (word.indexOf('sses', word.length - 4) !== -1) return word.substring(0, word.length - 2);
    if (word.indexOf('ies', word.length - 3) !== -1) return word.substring(0, word.length - 2);
    if (word.indexOf('ss', word.length - 2) !== -1) return word;
    if (word.charAt(word.length - 1) === 's') return word.substring(0, word.length - 1);
    return word;
  },

  step1b: function (word) {
    var changed = false;
    if (word.indexOf('eed', word.length - 3) !== -1) {
      if (this.measure(word.substring(0, word.length - 3)) > 0) return word.substring(0, word.length - 1);
      return word;
    }
    if (word.indexOf('ed', word.length - 2) !== -1) {
      if (this.containsVowel(word.substring(0, word.length - 2))) {
        word = word.substring(0, word.length - 2);
        changed = true;
      }
    }
    if (!changed && word.indexOf('ing', word.length - 3) !== -1) {
      if (this.containsVowel(word.substring(0, word.length - 3))) {
        word = word.substring(0, word.length - 3);
        changed = true;
      }
    }
    if (changed) {
      if (word.indexOf('at', word.length - 2) !== -1 || word.indexOf('bl', word.length - 2) !== -1 || word.indexOf('iz', word.length - 2) !== -1) {
        return word + 'e';
      }
      if (this.doubleConsonant(word)) {
        var last = word.charAt(word.length - 1);
        if (last !== 'l' && last !== 's' && last !== 'z') return word.substring(0, word.length - 1);
        return word;
      }
      if (this.measure(word) === 1 && this.cvc(word)) return word + 'e';
      return word;
    }
    return word;
  },

  step1c: function (word) {
    if (word.charAt(word.length - 1) === 'y' && this.containsVowel(word.substring(0, word.length - 1))) {
      return word.substring(0, word.length - 1) + 'i';
    }
    return word;
  },

  step2: function (word) {
    var n = word.length;
    if (n < 4) return word;
    if (this.measure(word.substring(0, n - 1)) > 0) {
      var r = word;
      r = this.replaceSuffix(r, 'ational', 'ate');
      r = this.replaceSuffix(r, 'tional', 'tion');
      r = this.replaceSuffix(r, 'enci', 'ence');
      r = this.replaceSuffix(r, 'anci', 'ance');
      r = this.replaceSuffix(r, 'izer', 'ize');
      r = this.replaceSuffix(r, 'abli', 'able');
      r = this.replaceSuffix(r, 'alli', 'al');
      r = this.replaceSuffix(r, 'entli', 'ent');
      r = this.replaceSuffix(r, 'eli', 'e');
      r = this.replaceSuffix(r, 'ousli', 'ous');
      r = this.replaceSuffix(r, 'ization', 'ize');
      r = this.replaceSuffix(r, 'ation', 'ate');
      r = this.replaceSuffix(r, 'ator', 'ate');
      r = this.replaceSuffix(r, 'alism', 'al');
      r = this.replaceSuffix(r, 'iveness', 'ive');
      r = this.replaceSuffix(r, 'fulness', 'ful');
      r = this.replaceSuffix(r, 'ousness', 'ous');
      r = this.replaceSuffix(r, 'aliti', 'al');
      r = this.replaceSuffix(r, 'iviti', 'ive');
      r = this.replaceSuffix(r, 'biliti', 'ble');
      return r;
    }
    return word;
  },

  step3: function (word) {
    var n = word.length;
    if (n < 3) return word;
    if (this.measure(word.substring(0, n - 1)) > 0) {
      var r = word;
      r = this.replaceSuffix(r, 'icate', 'ic');
      r = this.replaceSuffix(r, 'ative', '');
      r = this.replaceSuffix(r, 'alize', 'al');
      r = this.replaceSuffix(r, 'iciti', 'ic');
      r = this.replaceSuffix(r, 'ical', 'ic');
      r = this.replaceSuffix(r, 'ful', '');
      r = this.replaceSuffix(r, 'ness', '');
      return r;
    }
    return word;
  },

  step4: function (word) {
    var n = word.length;
    if (n < 2) return word;

    var checkSuffix = function (w, suffixes) {
      for (var i = 0; i < suffixes.length; i++) {
        if (w.indexOf(suffixes[i], w.length - suffixes[i].length) !== -1) return suffixes[i];
      }
      return null;
    };

    var sfx = checkSuffix(word, ['al', 'er', 'ic', 'able', 'ible', 'ant', 'ement', 'ment', 'ent', 'ou', 'ism', 'ate', 'iti', 'ous', 'ive', 'ize']);
    if (sfx !== null && this.measure(word) > 1) {
      return word.substring(0, n - sfx.length);
    }

    if (word.indexOf('ion', word.length - 3) !== -1) {
      var base = word.substring(0, n - 3);
      if (base.length > 0) {
        var prev = base.charAt(base.length - 1);
        if ((prev === 's' || prev === 't') && this.measure(base) > 1) {
          return base;
        }
      }
    }

    sfx = checkSuffix(word, ['ance', 'ence']);
    if (sfx !== null && this.measure(word) > 1) {
      return word.substring(0, n - 4);
    }

    return word;
  },

  step5a: function (word) {
    if (word.charAt(word.length - 1) === 'e') {
      var base = word.substring(0, word.length - 1);
      if (this.measure(base) > 1) return base;
      if (this.measure(base) === 1 && !this.cvc(base)) return base;
    }
    return word;
  },

  step5b: function (word) {
    if (word.length >= 4 && word.indexOf('ll', word.length - 2) !== -1 && this.measure(word) > 1) {
      return word.substring(0, word.length - 1);
    }
    return word;
  },

  stem: function (word) {
    if (word.length <= 2) return word;
    var s = word.toLowerCase();
    s = this.step1a(s);
    s = this.step1b(s);
    s = this.step1c(s);
    s = this.step2(s);
    s = this.step3(s);
    s = this.step4(s);
    s = this.step5a(s);
    s = this.step5b(s);
    return s;
  }
};

function levenshtein(a, b) {
  if (a.length === 0) return b.length;
  if (b.length === 0) return a.length;
  var matrix = [];
  for (var i = 0; i <= b.length; i++) { matrix[i] = [i]; }
  for (var j = 0; j <= a.length; j++) { matrix[0][j] = j; }
  for (i = 1; i <= b.length; i++) {
    for (j = 1; j <= a.length; j++) {
      var cost = a.charAt(j - 1) === b.charAt(i - 1) ? 0 : 1;
      matrix[i][j] = Math.min(
        matrix[i - 1][j] + 1,
        matrix[i][j - 1] + 1,
        matrix[i - 1][j - 1] + cost
      );
    }
  }
  return matrix[b.length][a.length];
}

function normalizeText(text) {
  return text.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
}

function tokenizeQuery(query, stopWords) {
  var normalized = normalizeText(query);
  if (!normalized) return [];
  var parts = normalized.split(/\s+/);
  var result = [];
  for (var i = 0; i < parts.length; i++) {
    var p = parts[i].trim();
    if (p && stopWords.indexOf(p) < 0) {
      result.push({ raw: p, stemmed: PorterStemmer.stem(p) });
    }
  }
  return result;
}

// Fuzzy find similar terms
function fuzzyFind(term, allTerms, maxDist) {
  var results = [];
  for (var i = 0; i < allTerms.length; i++) {
    var dist = levenshtein(term, allTerms[i]);
    if (dist <= maxDist && dist > 0) {
      results.push({ term: allTerms[i], distance: dist });
    }
  }
  results.sort(function (a, b) { return a.distance - b.distance; });
  return results;
}

function highlightSnippet(searchableText, termPositions, queryTerms) {
  if (!searchableText || !termPositions || !termPositions.length) return '';

  var SNIPPET_RADIUS = 55;
  var snippets = [];

  for (var i = 0; i < termPositions.length; i++) {
    var pos = termPositions[i];
    var start = Math.max(0, pos - SNIPPET_RADIUS);
    var end = Math.min(searchableText.length, pos + SNIPPET_RADIUS);
    snippets.push({ start: start, end: end, pos: pos });
  }

  snippets.sort(function (a, b) { return a.start - b.start; });
  var merged = [];
  for (i = 0; i < snippets.length; i++) {
    if (merged.length === 0) {
      merged.push(snippets[i]);
    } else {
      var last = merged[merged.length - 1];
      if (snippets[i].start <= last.end) {
        last.end = Math.max(last.end, snippets[i].end);
        last.pos = Math.max(last.pos, snippets[i].pos);
      } else {
        merged.push(snippets[i]);
      }
    }
  }

  var html = '';
  for (i = 0; i < merged.length; i++) {
    if (i > 0) html += ' ... ';
    var seg = merged[i];
    var excerpt = searchableText.substring(seg.start, seg.end);

    // Bold the matching terms
    for (var t = 0; t < queryTerms.length; t++) {
      var term = queryTerms[t];
      if (!term) continue;
      var re = new RegExp('(' + term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi');
      excerpt = excerpt.replace(re, '<b>$1</b>');
    }
    html += excerpt;
  }

  return html;
}

function searchInIndex(index, query) {
  if (!query || !query.trim()) return [];
  var queryTerms = tokenizeQuery(query, index.s || []);
  if (!queryTerms.length) return [];

  var docScores = {};
  var docTermPositions = {};
  var docMatchCount = {};
  var hasExactMatch = {};

  for (var t = 0; t < queryTerms.length; t++) {
    var qt = queryTerms[t];
    var hits = index.i[qt.stemmed];
    var isExact = false;

    if (hits) {
      isExact = true;
      for (var h = 0; h < hits.length; h++) {
        var entry = hits[h];
        var docIdx = entry[0];
        var score = entry[1];
        var positions = entry[2] || [];

        docScores[docIdx] = (docScores[docIdx] || 0) + score;
        docMatchCount[docIdx] = (docMatchCount[docIdx] || 0) + 1;
        if (positions.length) {
          if (!docTermPositions[docIdx]) docTermPositions[docIdx] = [];
          for (var p = 0; p < positions.length; p++) {
            docTermPositions[docIdx].push(positions[p]);
          }
        }
      }
    } else {
      // Fuzzy fallback
      var similar = fuzzyFind(qt.raw, index.t || [], 2);
      for (var s = 0; s < similar.length; s++) {
        var fuzzyStemmed = PorterStemmer.stem(similar[s].term);
        var fuzzyHits = index.i[fuzzyStemmed];
        if (fuzzyHits) {
          for (h = 0; h < fuzzyHits.length; h++) {
            entry = fuzzyHits[h];
            docIdx = entry[0];
            score = entry[1] * 0.8;
            positions = entry[2] || [];

            docScores[docIdx] = (docScores[docIdx] || 0) + score;
            docMatchCount[docIdx] = (docMatchCount[docIdx] || 0) + 1;
            if (positions.length) {
              if (!docTermPositions[docIdx]) docTermPositions[docIdx] = [];
              for (p = 0; p < positions.length; p++) {
                docTermPositions[docIdx].push(positions[p]);
              }
            }
          }
        }
      }
    }
    if (isExact) {
      if (hits) {
        for (h = 0; h < hits.length; h++) {
          hasExactMatch[hits[h][0]] = true;
        }
      }
    }
  }

  // Collect results
  var results = [];
  var docIds = Object.keys(docScores);
  for (var d = 0; d < docIds.length; d++) {
    var id = parseInt(docIds[d], 10);
    var doc = index.d[id];
    if (!doc) continue;
    results.push({
      docIdx: id,
      doc: doc,
      score: docScores[id],
      matchCount: docMatchCount[id] || 0,
      positions: docTermPositions[id] || [],
      exact: !!hasExactMatch[id]
    });
  }

  // Sort: exact matches first, then by score desc, then by match count desc
  results.sort(function (a, b) {
    if (a.exact !== b.exact) return a.exact ? -1 : 1;
    if (b.score !== a.score) return b.score - a.score;
    return b.matchCount - a.matchCount;
  });

  return results.slice(0, 20);
}

// ---- wire up search UI ----

$(document).ready(function () {
  var searchIndex = null;
  var hitCount = 0;
  var hits = $('#searchModal div.search-hits');

  $.getJSON('//www.yupiik.io/fusion/search.json', function (data) {
    searchIndex = data;
  });

  function executeSearch(query) {
    hits.empty();

    if (!searchIndex) {
      // Index not loaded yet, try again in 100ms
      setTimeout(function () { executeSearch(query); }, 100);
      return;
    }

    var results = searchInIndex(searchIndex, query);

    if (!results.length) {
      var div = $('<div class="text-center">No results matching <strong>' +
          $('<span>').text(query).html() +
          '</strong> found.</div>');
      hits.append(div);
      return;
    }

    for (var i = 0; i < results.length; i++) {
      var r = results[i];
      var doc = r.doc;
      var title = doc.t || '';
      var url = doc.u || '';
      var desc = doc.m || '';

      // Build snippet
      var snippetText = doc.x || desc || '';
      var snippet = '';
      if (r.positions.length) {
        var rawQueryTerms = [];
        var qtokens = tokenizeQuery(query, searchIndex.s || []);
        for (var q = 0; q < qtokens.length; q++) {
          rawQueryTerms.push(qtokens[q].raw);
        }
        snippet = highlightSnippet(snippetText, r.positions, rawQueryTerms);
      }
      if (!snippet && desc) {
        snippet = desc;
      }

      var link = $('<a>').attr('href', url).text(title);
      var card = $('<div class="search-result-container"></div>').append(link);
      if (snippet) {
        card.append($('<p></p>').html(snippet));
      }
      hits.append(card);
    }
  }

  $('#searchInput').on('input', function () {
    executeSearch($(this).val());
  });

  $('#search-button').click(function () {
    setTimeout(function () {
      hits.empty();
    }, 100);
  });
});