package cn.gavin.tdd.restful;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConverterConstructorTest {

    @Test
    public void should_convert_via_converter_constructor(){
       assertEquals(Optional.of(new BigDecimal("12345")), ConverterConstructor.convert(BigDecimal.class,"12345"));
    }
}