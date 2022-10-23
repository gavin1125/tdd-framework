package cn.gavin.tdd.restful;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

public class SubResourceLocatorsTest {

    @ParameterizedTest(name = "{2}")
    @CsvSource(textBlock = """
            /hello,           Messages.hello,       fully matched with URI
            /hello/content,   Messages.hello,       matched with URI
            /topics/1234,     Messages.message1234, multiple matched choices  
            """)
    public void should_match_path_with_uri(String path, String resourceMethod, String context) {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());

        ResourceRouter.SubResourceLocator locator = locators.findSubResource(path).get();

        assertEquals(resourceMethod, locator.toString());
    }

    @Test
    public void should_return_empty_if_not_match_uri() {
        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());
        assertTrue(locators.findSubResource("/missing").isEmpty());
    }

    @Test
    public void should_call_locator_method_to_generate_sub_resource() {
        UriInfoBuilder infoBuilder = new StubUriInfoBuilder();
        infoBuilder.addMatchedResource(new Messages());

        SubResourceLocators locators = new SubResourceLocators(Messages.class.getMethods());
        ResourceRouter.SubResourceLocator subResourceLocator = locators.findSubResource("/hello").get();

        ResourceRouter.Resource subResource = subResourceLocator.getSubResource(Mockito.mock(ResourceContext.class), infoBuilder);

        UriTemplate.MatchResult result = Mockito.mock(UriTemplate.MatchResult.class);
        when(result.getRemaining()).thenReturn(null);

        ResourceRouter.ResourceMethod method = subResource.match(result, "GET", new String[]{MediaType.TEXT_PLAIN}, null, infoBuilder).get();
        assertEquals("Message.content", method.toString());
        assertEquals("hello",((Message)infoBuilder.getLastMatchedResource()).message);
    }

    @Path("/messages")
    static class Messages {
        @Path("/hello")
        public Message hello() {
            return new Message("hello");
        }

        @Path("/topics/{id}")
        public Message id() {
            return new Message("id");
        }

        @Path("/topics/1234")
        public Message message1234() {
            return new Message("1234");
        }
    }

    static class Message {
        private String message;

        public Message(String message) {
            this.message = message;
        }

        @GET
        public String content() {
            return "content";
        }
    }

}
