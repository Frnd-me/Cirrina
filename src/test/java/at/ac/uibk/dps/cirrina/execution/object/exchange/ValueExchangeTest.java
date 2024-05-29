package at.ac.uibk.dps.cirrina.execution.object.exchange;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class ValueExchangeTest {

  @Test
  public void testToFromBytes() {
    final var i = 1;
    final var f = 1.0f;
    final var l = 1L;
    final var d = 1.0;
    final var s = "1";
    final var bo = true;
    final var by = new byte[] { 8, 1, 16, 0, 0, 0, 63, 24, 0, 0, 0, 0, 0, 0, 0, 0, 33, 8, 49, 16, 1, 26, 1, 49, 8, 32 };

    assertDoesNotThrow(() -> {
      assertEquals(i, ValueExchange.fromBytes(new ValueExchange(i).toBytes()).getValue());
      assertEquals(f, ValueExchange.fromBytes(new ValueExchange(f).toBytes()).getValue());
      assertEquals(l, ValueExchange.fromBytes(new ValueExchange(l).toBytes()).getValue());
      assertEquals(d, ValueExchange.fromBytes(new ValueExchange(d).toBytes()).getValue());
      assertEquals(s, ValueExchange.fromBytes(new ValueExchange(s).toBytes()).getValue());
      assertEquals(bo, ValueExchange.fromBytes(new ValueExchange(bo).toBytes()).getValue());
      assertArrayEquals(by, (byte[]) ValueExchange.fromBytes(new ValueExchange(by).toBytes()).getValue());
    });
  }
}
