package cn.gavin.tdd.restful;

import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubResourceLocatorsTest {

    @Test
    public void should_math_path_with_uri() {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());

        ResourceRouter.SubResourceLocator locator = locators.findSubResource("/hello").get();

        assertEquals("Messages.hello", locator.toString());
    }

    @Test
    public void should_return_empty_if_not_match_uri(){
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());
       assertTrue(locators.findSubResource("/missing").isEmpty());
    }


    @Path("/messages")
    static class Messages {
        @Path("/hello")
        public Message hello() {
            return new Message();
        }
    }

    static class Message {

    }
}
