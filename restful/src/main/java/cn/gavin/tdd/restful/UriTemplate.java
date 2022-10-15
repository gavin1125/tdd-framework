package cn.gavin.tdd.restful;

import java.util.Map;
import java.util.Optional;

interface UriTemplate {
    //@Path("{id}") /1/orders
    interface MatchResult extends Comparable<MatchResult> {
        String getMatched(); //1

        String getRemaining(); //orders

        Map<String, String> getMatchedPathParameters(); //id->1
    }

    Optional<MatchResult> match(String path);
}
