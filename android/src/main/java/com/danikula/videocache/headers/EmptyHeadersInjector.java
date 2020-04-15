package com.danikula.videocache.headers;

/**
 * Empty {@link HeaderInjector} implementation.
 *
 * @author Lucas Nelaupe (https://github.com/lucas34).
 */
public class EmptyHeadersInjector implements HeaderInjector {

    @Override
    public CustomHeaders addHeaders(String url) {
        return new CustomHeaders();
    }

}
