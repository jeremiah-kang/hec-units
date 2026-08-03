package mil.army.usace.hec.units.viz;

import java.util.List;
import java.util.Map;

import mil.army.usace.hec.graph.viz.formula.AffineForm;
import mil.army.usace.hec.graph.viz.formula.FormulaRenderer;
import mil.army.usace.hec.graph.viz.model.EdgeStatus;
import mil.army.usace.hec.graph.viz.view.Html;
import net.hobbyscience.SimplePostfixCalculator;
import net.hobbyscience.database.Conversion;

/**
 * Describes what one conversion does, as the panel shown when a cell is picked:
 * the pair in plain English, the net factor, the hops behind it, and the tests
 * that touched it.
 */
final class ConversionDetail {

    /*
     * These stay flat, unlike the templates that build the page itself. This
     * markup is escaped into a data-detail attribute on every cell, so it is a
     * payload rather than structure - nobody reads it as source, and indenting
     * it added 67KB across the 404 cells that carry a copy.
     */
    private static final String PANEL = """
        <div class="fx">
        <div class="fx-head">{{from}}<span class="arrow">→</span>{{to}}{{chip}}</div>
        {{names}}
        <div class="fx-eq">{{lhs}}<span class="eq">=</span>{{rhs}}</div>
        {{chain}}
        {{tests}}
        </div>
        """;

    private static final String CHAIN = """
        <div class="fx-where"><span class="kw">via</span>{{steps}}
        <span class="hopcount">{{hops}}</span></div>
        """;

    private static final String TESTS = """
        <div class="fx-tests">
        <div class="lbl">{{count}}</div>
        {{rows}}
        </div>
        """;

    private static final String TEST_ROW = """
        <div class="tc {{tone}}">
        <div class="tc-top">{{input}} {{fromUnit}}<span class="arrow">→</span>{{got}} {{toUnit}}
        <span class="tc-mark">{{mark}}</span></div>
        <div class="tc-sub"><span>expected <b>{{want}}</b></span>
        <span>error <b>{{error}}</b>{{percent}}</span>
        <span>tolerance ±{{delta}}</span></div>
        </div>
        """;

    private ConversionDetail() {
    }

    // Conversions chained together, counted from the hop chain
    static int hops(Conversion conversion) {
        String chain = conversion.getConversionChain();
        if (chain == null || chain.isBlank()) {
            return 1;
        }
        return Math.max(1, chain.split("->").length - 1);
    }

    static String of(Conversion conversion, String inversePostfix, EdgeStatus status,
                     Map<String, String> names, List<TestCase> direct, List<TestCase> roundTrip) {
        final String postfix;
        try {
            postfix = conversion.getMethod().getPostfix();
        } catch (Exception e) {
            return null;                    // unreadable formula, not worth failing the page
        }

        String from = conversion.getFrom().getAbbreviation();
        String to = conversion.getTo().getAbbreviation();

        return Html.fill(PANEL)
            .raw("from", UnitFormat.unit(from))
            .raw("to", UnitFormat.unit(to))
            .raw("chip", chip(status))
            .raw("names", names(names.get(from), names.get(to)))
            .raw("lhs", UnitFormat.unit(to))
            .raw("rhs", equation(postfix, from))
            .raw("chain", chain(conversion))
            .raw("tests", tests(postfix, inversePostfix, from, to, direct, roundTrip))
            .render();
    }

    private static String names(String fromName, String toName) {
        if (fromName == null || toName == null) {
            return "";
        }
        return Html.tag("div").attr("class", "fx-names").text(fromName + " to " + toName).toString();
    }

    private static String chip(EdgeStatus status) {
        String label = status == EdgeStatus.PASSED ? "passed"
                     : status == EdgeStatus.FAILED ? "failed" : "not tested";
        String cls = status == EdgeStatus.PASSED ? "passed"
                   : status == EdgeStatus.FAILED ? "failed" : "untested";
        return Html.tag("span").attr("class", "chip " + cls).text(label).toString();
    }

    // Derived conversions expose only postfix, so the affine probe runs against that
    private static String equation(String postfix, String from) {
        AffineForm form = FormulaRenderer.affineOf(x -> SimplePostfixCalculator.calculate(postfix, x));

        if (form == null) {
            return warn("not a simple scale + offset");
        }
        if (form.m() == 0.0) {
            // A zero scale means the input is ignored, which is always a bug.
            return warn("ignores its input - always " + FormulaRenderer.formatNumber(form.b()));
        }

        var out = new StringBuilder(UnitFormat.unit(from));
        if (form.m() != 1.0) {
            out.append(op("×")).append(FormulaRenderer.formatNumber(form.m()));
        }
        if (form.b() != 0.0) {
            out.append(op(form.b() > 0 ? "+" : "−"))
               .append(FormulaRenderer.formatNumber(Math.abs(form.b())));
        }
        return out.toString();
    }

    private static String warn(String text) {
        return Html.tag("span").attr("class", "warn").text(text).toString();
    }

    private static String op(String symbol) {
        return Html.tag("span").attr("class", "op").text(symbol).toString();
    }

    // When a derived conversion is wrong it is usually one hop in the middle
    private static String chain(Conversion conversion) {
        String chain = conversion.getConversionChain();
        if (chain == null || chain.isBlank()) {
            return "";
        }
        int hops = hops(conversion);
        var steps = new StringBuilder();
        String[] parts = chain.split("->");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                steps.append("<span class=\"arrow\">→</span>");
            }
            steps.append(UnitFormat.unit(parts[i].trim()));
        }
        return Html.fill(CHAIN)
            .raw("steps", steps.toString())
            .put("hops", hops + (hops == 1 ? " hop" : " hops"))
            .render();
    }

    /**
     * Every test that runs this conversion, in this direction.
     *
     * A CSV row written the other way still exercises this conversion: the suite
     * converts across and then back, and the trip home is this one. It is listed
     * as its own test, with the intermediate value the suite actually feeds in.
     */
    private static String tests(String postfix, String inversePostfix, String from, String to,
                                List<TestCase> direct, List<TestCase> roundTrip) {
        boolean canRoundTrip = inversePostfix != null;
        int count = direct.size() + (canRoundTrip ? roundTrip.size() : 0);
        if (count == 0) {
            return "<div class=\"fx-tests\"><div class=\"lbl\">no test covers this pair</div></div>";
        }

        var rows = new StringBuilder();
        for (TestCase test : direct) {
            rows.append(testRow(from, to, test.input(),
                                evaluate(postfix, test.input()), test.expected(), test.delta()));
        }
        if (canRoundTrip) {
            for (TestCase test : roundTrip) {
                Double there = evaluate(inversePostfix, test.input());
                Double back = there == null ? null : evaluate(postfix, there);
                rows.append(testRow(from, to, there, back, test.input(), test.inverseDelta()));
            }
        }

        return Html.fill(TESTS)
            .put("count", count + (count == 1 ? " test case" : " test cases"))
            .raw("rows", rows.toString())
            .render();
    }

    private static Double evaluate(String postfix, double input) {
        try {
            double value = SimplePostfixCalculator.calculate(postfix, input);
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String testRow(String from, String to,
                                  Double input, Double got, double want, double delta) {
        if (input == null || got == null) {
            return "<div class=\"tc bad\"><div class=\"tc-top\">could not evaluate</div></div>";
        }

        double off = Math.abs(got - want);
        boolean ok = off <= delta;

        return Html.fill(TEST_ROW)
            .put("tone", ok ? "ok" : "bad")
            .put("input", FormulaRenderer.formatNumber(input))
            .raw("fromUnit", UnitFormat.unit(from))
            .put("got", FormulaRenderer.formatNumber(got))
            .raw("toUnit", UnitFormat.unit(to))
            .put("mark", ok ? "✓ passed" : "✗ failed")
            .put("want", FormulaRenderer.formatNumber(want))
            // The size of the miss is the diagnosis: rounding-level means the
            // tolerance is tight, large means a wrong constant.
            .put("error", FormulaRenderer.formatNumber(off))
            .put("percent", relativeError(off, want))
            .put("delta", FormulaRenderer.formatNumber(delta))
            .render();
    }

    /**
     * The miss as a share of the expected value, which is what says whether it
     * matters: 1e-9 off a billion is rounding, 1e-9 off 1e-9 is a wrong formula.
     */
    private static String relativeError(double off, double want) {
        if (want == 0 || off == 0) {
            return "";
        }
        double percent = off / Math.abs(want) * 100;
        if (percent < 0.001) {
            return " (<0.001%)";
        }
        return " (" + FormulaRenderer.formatNumber(round(percent, 3)) + "%)";
    }

    private static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
