package cn.gavin.tdd.restful;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Test;

public class ResourceTest {

    @Test
    public void should() {
        RootResourceClass root = new RootResourceClass(Users.class);
        UriTemplate uriTemplate = root.getUriTemplate();

    }

    @Path("/users")
    static class Users {
        @GET
        public String get() {
            return "users";
        }
    }

    @Path("/users/{id}")
    static class User {
        @GET
        public String get() {
            return "users";
        }
    }
}
