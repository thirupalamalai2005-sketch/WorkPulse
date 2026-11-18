package com.workpulse.unit;

import com.workpulse.service.CsvWriter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvWriterTest {

    @Test
    void plainValuesAreWrittenAsIs() {
        assertEquals("abc", CsvWriter.cell("abc"));
        assertEquals("", CsvWriter.cell(null));
        assertEquals("42", CsvWriter.cell(42));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"a,b\"", CsvWriter.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvWriter.cell("line1\nline2"));
    }

    @Test
    void formulaPrefixesAreNeutralised() {
        assertEquals("'=1+1", CsvWriter.cell("=1+1"));
        assertEquals("'+cmd", CsvWriter.cell("+cmd"));
        assertEquals("'-2", CsvWriter.cell("-2"));
        assertEquals("'@SUM(A1)", CsvWriter.cell("@SUM(A1)"));
    }

    @Test
    void rowJoinsCellsWithCrLf() {
        assertEquals("a,\"b,c\",\r\n", CsvWriter.row("a", "b,c", null));
    }
}
