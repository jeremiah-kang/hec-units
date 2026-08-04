package mil.army.usace.hec.graph.viz.view;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

// Wraps view fragments in a complete, self-contained HTML document
public final class PageRenderer {

    private static final String PAGE = """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>{{title}}</title>
            <script>
              // Settle the theme before the first paint, or the page flashes
              // the wrong one. A saved choice outranks the system setting.
              (function () {
                var saved = null;
                try { saved = localStorage.getItem('viz-theme'); } catch (e) { }
                var dark = window.matchMedia
                        && matchMedia('(prefers-color-scheme:dark)').matches;
                document.documentElement.dataset.theme = saved || (dark ? 'dark' : 'light');
              })();
            </script>
            <style>
        {{css}}
            </style>
          </head>
          <body>
            <div class="pagehead">
              <div>
                <h1>{{title2}}</h1>
                <p class="lede">{{lede}}</p>
              </div>
              <div class="headbtns">
                {{summaryButton}}
                {{themeButton}}
              </div>
            </div>
            {{tabbar}}
            <div class="tabpane active" id="tab-coverage">
              {{legend}}
              {{coverbar}}
              {{body}}
            </div>
            {{seedpane}}
            {{findpane}}
            {{overlay}}
            {{summary}}
            {{data}}
            <script>
        {{cyto}}
            </script>
            <script>
        {{js}}
            </script>
          </body>
        </html>
        """;

    // Both icons ship; CSS shows whichever one names the theme you would get.
    private static final String THEME_BUTTON = """
        <button id="themetoggle" type="button" title="Switch between light and dark">
          <svg class="i-sun" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="4.1"/>
            <path d="M12 2.2v2.5M12 19.3v2.5M4.5 4.5l1.8 1.8M17.7 17.7l1.8 1.8
                     M2.2 12h2.5M19.3 12h2.5M4.5 19.5l1.8-1.8M17.7 6.3l1.8-1.8"/>
          </svg>
          <svg class="i-moon" viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20.3 14.6A8.4 8.4 0 0 1 9.4 3.7 8.6 8.6 0 1 0 20.3 14.6Z"/>
          </svg>
          <span class="tlabel"></span>
        </button>
        """;

    private static final String OVERLAY = """
        <div id="overlay">
          <div id="obar">
            <h3 id="otitle"></h3>
            <span id="oaxis">row → column</span>
            <span id="otally" class="tally"></span>
            <label class="find" id="ofind">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/>
              <path d="M16.5 16.5 21 21"/></svg>
              <input type="search" placeholder="highlight a unit">
              <button type="button" class="clearfind" aria-label="clear">&times;</button>
            </label>
            <button id="oreset" type="button">Reset view</button>
            <button id="oclose" type="button">Close</button>
          </div>
          {{legend}}
          {{seedkey}}
          <div id="ostagewrap">
            <div id="ostage"></div>
            <aside id="opanel"><div id="odetail"></div></aside>
          </div>
        </div>
        """;

    private static final String SUMMARY = """
        <div id="summary">
          <div id="sbar"><h3>Test suite summary</h3><button id="sclose" type="button">Close</button></div>
          <div id="sbody">{{content}}</div>
        </div>
        """;

    private static final String KEY = """
        <button type="button" class="key" data-status="{{cls}}"
                title="Search for these">
          <span class="k-top"><i class="sw {{cls}}"></i>{{label}}</span>{{counts}}
        </button>
        """;

    private PageRenderer() {
    }

    public static String render(String title, Stats stats, String body, String seedBody,
                                String data) throws IOException {
        boolean tabbed = !seedBody.isEmpty();
        return Html.fill(PAGE)
            .put("title", title)
            .put("title2", title)
            .put("lede", tabbed
                 ? "Coverage shows every conversion the algorithm can produce, one matrix per "
                 + "dimension - each row converts into the columns. Conversion graphs show the "
                 + "direct conversions those are all derived from - the ones written by hand, "
                 + "one step each. Click any card to enlarge it."
                 : "Every conversion the algorithm can produce, one matrix per dimension. "
                 + "Each row converts into the columns. Click a card to enlarge it, then click "
                 + "any cell for its equation and test results.")
            .raw("css", resource("/viz.css"))
            .raw("js", pageScript())
            .raw("cyto", tabbed ? resource("/cytoscape.min.js") : "")
            .raw("themeButton", THEME_BUTTON)
            .raw("summaryButton", stats == null ? ""
                 : "<button id=\"sumopen\" type=\"button\">Summary</button>")
            .raw("tabbar", tabbed ? TABBAR : "")
            .raw("legend", legend("legend", stats))
            .raw("body", body)
            .raw("coverbar", tabbed ? SearchView.bar("cover",
                     "Filter dimensions - try Volume, " + Labels.plain("ft3") + ", or Length", List.of(
                     new SearchView.Group("coverage", List.of(
                     new SearchView.Option("failed", "has failures"),
                     new SearchView.Option("untested", "incomplete"),
                     new SearchView.Option("complete", "fully covered"))))) : "")
            .raw("seedpane", tabbed
                 ? Html.fill(SEEDPANE).raw("legend", seedLegend())
                       .raw("bar", SearchView.bar("seed",
                            "Filter dimensions - try Volume, " + Labels.plain("ft3") + ", or Length", List.of(
                            new SearchView.Group("graph shape", List.of(
                            new SearchView.Option("tree", "trees"),
                            new SearchView.Option("cyclic", "has cycles"),
                            new SearchView.Option("dup", "duplicate edges"))))))
                       .raw("body", seedBody).render()
                 : "")
            .raw("findpane", tabbed ? SearchView.tab() : "")
            .raw("overlay", Html.fill(OVERLAY)
                 .raw("legend", legend("legend olegend mkey", null))
                 .raw("seedkey", tabbed ? seedKey() : "")
                 .render())
            .raw("summary", summary(stats))
            .raw("data", data.isEmpty() ? "" : "<script>\n" + data + "</script>")
            .render();
    }

    private static final String TABBAR = """
        <div class="tabs" role="tablist">
          <button class="tab active" data-pane="tab-coverage" type="button">Coverage</button>
          <button class="tab" data-pane="tab-seed" type="button">Conversion graphs</button>
          <button class="tab" data-pane="tab-find" type="button">Search</button>
          <span class="tabink"></span>
        </div>
        """;

    private static final String SEEDPANE = """
        <div class="tabpane" id="tab-seed">
          {{legend}}
          {{bar}}
          {{body}}
        </div>
        """;

    private static final String LEGEND = """
        <div class="{{class}}">
          {{keys}}
          <span class="hint">numbers in cells are hops in the chosen route</span>
        </div>
        """;

    private static final String TOTAL = """
        <span class="key total">
          <span class="k-top">total</span>
          <span class="k-num">{{count}}</span>
        </span>
        """;

    // The graph tab's own key: node systems and the two stroke kinds
    private static String seedLegend() {
        return """
            <div class="legend seedlegend">
              <button type="button" data-system="SI"><i class="sw t-si"></i>SI</button>
              <button type="button" data-system="English"><i class="sw t-english"></i>English</button>
              <button type="button" data-system="NULL"><i class="sw t-null"></i>system-agnostic</button>
              <button type="button" data-kind="linear"><i class="ln"></i><code>linear:</code> scale + offset</button>
              <button type="button" data-kind="function"><i class="ln dash"></i><code>function:</code> arbitrary expression</button>
              <span class="hint">a bowed pair is two different conversions for the same two units</span>
            </div>
            """;
    }

    // The same key on the dark backdrop, shown only while a graph is enlarged
    private static String seedKey() {
        return """
            <div class="legend olegend skey">
              <button type="button" data-system="SI"><i class="sw t-si"></i>SI</button>
              <button type="button" data-system="English"><i class="sw t-english"></i>English</button>
              <button type="button" data-system="NULL"><i class="sw t-null"></i>system-agnostic</button>
              <button type="button" data-kind="linear"><i class="ln"></i>linear</button>
              <button type="button" data-kind="function"><i class="ln dash"></i>function</button>
              <span class="hint">click two units for routes · click an edge for its formula</span>
            </div>
            """;
    }

    private static String legend(String className, Stats stats) {
        return Html.fill(LEGEND)
            .put("class", className)
            .raw("keys", key(stats, "passed", "passed")
                       + key(stats, "failed", "failed")
                       + key(stats, "untested", "reachable, not tested")
                       + key(stats, "missing", "no conversion")
                       + total(stats))
            .render();
    }

    private static String key(Stats stats, String cls, String label) {
        return Html.fill(KEY)
            .put("cls", cls)
            .put("label", label)
            .raw("counts", stats == null ? "" : counts(stats, cls))
            .render();
    }

    // The denominator the percentages above are shares of
    private static String total(Stats stats) {
        if (stats == null) {
            return "";
        }
        return Html.fill(TOTAL).put("count", stats.pairs()).render();
    }

    private static String counts(Stats stats, String cls) {
        int count = switch (cls) {
            case "passed" -> stats.passed();
            case "failed" -> stats.failed();
            case "untested" -> stats.untested();
            default -> stats.missing();
        };
        return Html.fill("<span class=\"k-num\">{{count}}<em>{{share}}</em></span>")
            .put("count", count)
            .put("share", Stats.percent(stats.percentOfPairs(count)))
            .render();
    }

    private static String summary(Stats stats) {
        if (stats == null) {
            return "";
        }
        return Html.fill(SUMMARY).raw("content", SummaryView.render(stats, "Route length")).render();
    }

    private static final List<String> SCRIPTS = List.of(
        "/viz/overlay.js",        // the enlarge overlay: open, close, detail panel
        "/viz/tables.js",         // click-to-sort table headers
        "/viz/graph.js",          // conversion-graph explorer (cytoscape + physics)
        "/viz/chrome.js",         // tab bar and summary modal
        "/viz/matrix-cells.js",   // hover and pin for enlarged matrix cells
        "/viz/search.js");        // search tab, grid filters, in-matrix highlight

    private static String pageScript() throws IOException {
        var script = new StringBuilder("(function () {\n");
        for (String path : SCRIPTS) {
            script.append(resource(path));
        }
        return script.append("})();\n").toString();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = PageRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException(path + " is missing from the graph-viz resources");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
