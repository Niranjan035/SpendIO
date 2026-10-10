package com.spendio.imports.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendio.imports.parser.CsvStreamingReader.CsvRow;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Parser stage (ARCHITECTURE.md D.12 step 2) against the fixture files: delimiter and
 * encoding detection, quoted-field fidelity, header detection, and the hard caps.
 */
class CsvParserTest {

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = CsvParserTest.class.getResourceAsStream("/fixtures/imports/" + name)) {
            assertThat(in).as("fixture " + name + " on the test classpath").isNotNull();
            return in.readAllBytes();
        }
    }

    private static String read(String name) throws IOException {
        return new String(fixture(name), StandardCharsets.UTF_8);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "comma-simple.csv, ','",
            "semicolon-eu.csv, ';'",
            "two-amount-columns.csv, ','",
            "bank-two-column-money.csv, ';'",
            "with-accounts.csv, ','"
    })
    void detectsTheDelimiterFromTheHeader(String file, char expected) throws IOException {
        CsvFormatDetector.CsvFormat format = CsvFormatDetector.detect(fixture(file), null, null);
        assertThat(format.delimiter()).isEqualTo(expected);
        assertThat(format.encoding()).isEqualTo("UTF-8");
    }

    @Test
    void overridesWinOverDetection() {
        byte[] sample = "a;b;c\n1;2;3\n".getBytes(StandardCharsets.UTF_8);
        CsvFormatDetector.CsvFormat format = CsvFormatDetector.detect(sample, "|", "UTF-8");
        assertThat(format.delimiter()).isEqualTo('|');
    }

    @Test
    void utf16BomIsDetected() {
        byte[] sample = "Date,Amount\n".getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[sample.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(sample, 0, withBom, 2, sample.length);

        CsvFormatDetector.CsvFormat format = CsvFormatDetector.detect(withBom, null, null);
        assertThat(format.encoding()).isEqualTo("UTF-16LE");
    }

    @Test
    void quotedFieldsKeepEmbeddedSeparatorsAndEscapedQuotes() {
        String content = "Date,Description,Amount\n"
                + "2026-03-01,\"Coffee, large \"\"double\"\"\",-4.50\n";

        List<CsvRow> rows = CsvStreamingReader.read(content, ',');

        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).fields())
                .containsExactly("2026-03-01", "Coffee, large \"double\"", "-4.50");
    }

    @Test
    void crlfLineEndingsDoNotLeakIntoFields() {
        String content = "Date,Amount\r\n2026-03-01,-4.50\r\n2026-03-02,-5.00\r\n";

        List<CsvRow> rows = CsvStreamingReader.read(content, ',');

        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).fields()).containsExactly("2026-03-01", "-4.50");
        assertThat(rows.get(2).fields()).containsExactly("2026-03-02", "-5.00");
    }

    @Test
    void trailingNewlineDoesNotProducePhantomRow() {
        List<CsvRow> rows = CsvStreamingReader.read("a,b\n1,2\n", ',');
        assertThat(rows).hasSize(2);
    }

    @Test
    void fixtureRowsParseToTheExpectedShape() throws IOException {
        List<CsvRow> rows = CsvStreamingReader.read(read("comma-simple.csv"), ',');

        assertThat(rows).hasSize(6);
        assertThat(rows.getFirst().fields()).containsExactly("Date", "Description", "Amount");
        assertThat(rows.get(1).fields()).containsExactly("2026-03-01", "Coffee", "-4.50");
        assertThat(rows.getLast().fields()).containsExactly("2026-03-05", "Rent", "-1200.00");
    }

    @Test
    void semicolonFixtureKeepsEuroDecimalsIntact() throws IOException {
        List<CsvRow> rows = CsvStreamingReader.read(read("semicolon-eu.csv"), ';');

        assertThat(rows).hasSize(6);
        assertThat(rows.get(1).fields()).containsExactly("01.03.2026", "Coffee", "-4,50");
    }

    @Test
    void headerDetectionAndCleaning() {
        assertThat(CsvHeaderAnalyzer.looksLikeHeader(List.of("Date", "Description", "Amount")))
                .isTrue();
        assertThat(CsvHeaderAnalyzer.looksLikeHeader(List.of("2026-03-01", "Coffee", "-4.50")))
                .isFalse();
        assertThat(CsvHeaderAnalyzer.looksLikeHeader(List.of("$1,234.56", "GBP 12", "1.234,56")))
                .isFalse();
        assertThat(CsvHeaderAnalyzer.looksLikeHeader(null)).isFalse();

        assertThat(CsvHeaderAnalyzer.cleanHeaders(List.of(" Date ", "  ", "Amount (debit)")))
                .containsExactly("Date", "column_2", "Amount (debit)");
    }

    @Test
    void readsPastTheRowCapSoTheParseStageCanFlagIt() {
        StringBuilder content = new StringBuilder("a,b\n");
        for (int i = 0; i < CsvStreamingReader.MAX_ROWS + 10; i++) {
            content.append(i).append(",x\n");
        }
        List<CsvRow> rows = CsvStreamingReader.read(content.toString(), ',');

        assertThat(rows.size()).isGreaterThan(CsvStreamingReader.MAX_ROWS);
        assertThat(CsvStreamingReader.MAX_ROWS).isEqualTo(50_000);
        assertThat(CsvStreamingReader.MAX_COLUMNS).isEqualTo(10_000);
    }
}