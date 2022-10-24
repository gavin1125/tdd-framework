package cn.gavin.tdd.restful;

import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class RootResourceTest {
    private ResourceContext resourceContext;
    private Messages rootResource;

    @BeforeEach
    public void before() {
        rootResource = new Messages();
        resourceContext = Mockito.mock(ResourceContext.class);
        when(resourceContext.getResource(eq(Messages.class))).thenReturn(rootResource);
    }

    @Test
    public void should_get_uri_template_from_path_annotation() {
        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate template = resource.getUriTemplate();

        assertTrue(template.match("/messages/hello").isPresent());
    }

    @ParameterizedTest(name = "{3}")
    @CsvSource(textBlock = """
            GET,     /messages,           Messages.get,        Map to resource method
            GET,     /messages/1/content, Message.content,     Map to sub-resource method  
            GET,     /messages/1/body,    MessageBody.get,     Map to sub-sub-resource method
            """)
    public void should_match_resource_in_root_resource(String httpMethod, String path, String resourceMethod, String context) {
        UriInfoBuilder builder = new StubUriInfoBuilder();
        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match(path).get();

        ResourceRouter.ResourceMethod method = resource.match(result, httpMethod, new String[]{MediaType.TEXT_PLAIN}, resourceContext, builder).get();

        assertEquals(resourceMethod, method.toString());
    }

    @ParameterizedTest(name = "{2}")
    @CsvSource(textBlock = """
            GET,          /messages/hello,      No matched resource method
            GET,          /messages/1/header,   No matched sub-resource method
            """)
    public void should_return_empty_if_not_matched_in_root_resource(String httpMethod, String uri, String context) {
        UriInfoBuilder builder = new StubUriInfoBuilder();
        builder.addMatchedResource(new Messages());

        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match(uri).get();

        assertTrue(resource.match(result, httpMethod, new String[]{MediaType.TEXT_PLAIN}, resourceContext, builder).isEmpty());
    }

    @Test
    public void should_add_last_match_resource_to_uri_info_builder() {
        StubUriInfoBuilder uriInfoBuilder = new StubUriInfoBuilder();

        ResourceRouter.Resource resource = new ResourceHandler(Messages.class);
        UriTemplate.MatchResult result = resource.getUriTemplate().match("/messages").get();

        resource.match(result, "GET", new String[]{MediaType.TEXT_PLAIN}, resourceContext, uriInfoBuilder);

        assertTrue(uriInfoBuilder.getLastMatchedResource() instanceof Messages);
    }

    // TODO: 10/20/22 if resource class does not have a path annotation, throw illegal argument
    @Test
    public void should_throw_illegal_argument_exception_if_root_resource_not_have_path_annotation() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceHandler(Message.class));
    }

    // TODO: 2022/10/22 Head and Options special case

    @Path("/messages")
    static class Messages {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "messages";
        }

        @Path("/special")
        @GET
        public String getSpecial() {
            return "special";
        }

        @HEAD
        public void head(){
        }

        @OPTIONS
        public void options(){
        }

        @Path("/{id:[0-9]+}")
        public Message getById() {
            return new Message();
        }
    }

    static class Message {
        @GET
        @Path("/content")
        @Produces(MediaType.TEXT_PLAIN)
        public String content() {
            return "content";
        }

        @Path("/body")
        public MessageBody body() {
            return new MessageBody();
        }
    }

    static class MessageBody {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String get() {
            return "body";
        }
    }
}