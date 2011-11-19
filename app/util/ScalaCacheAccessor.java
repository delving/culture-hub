package util;

import play.cache.Cache;
import play.cache.CacheImpl;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class ScalaCacheAccessor {

    public static void set(CacheImpl cacheImpl) {
        Cache.forcedCacheImpl = cacheImpl;
        Cache.cacheImpl = cacheImpl;
    }
}
