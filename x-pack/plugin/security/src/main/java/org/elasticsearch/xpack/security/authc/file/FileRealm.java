/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.security.authc.file;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.watcher.ResourceWatcherService;
import org.elasticsearch.xpack.core.security.authc.AuthenticationResult;
import org.elasticsearch.xpack.core.security.authc.RealmConfig;
import org.elasticsearch.xpack.core.security.authc.file.FileRealmSettings;
import org.elasticsearch.xpack.core.security.authc.support.UsernamePasswordToken;
import org.elasticsearch.xpack.core.security.user.User;
import org.elasticsearch.xpack.security.authc.support.CachingUsernamePasswordRealm;

import java.util.Map;

public class FileRealm extends CachingUsernamePasswordRealm {

    private final FileUserPasswdStore userPasswdStore;
    private final FileUserRolesStore userRolesStore;

    public FileRealm(RealmConfig config, ResourceWatcherService watcherService) {
        this(config, new FileUserPasswdStore(config, watcherService), new FileUserRolesStore(config, watcherService));
    }

    // pkg private for testing
    FileRealm(RealmConfig config, FileUserPasswdStore userPasswdStore, FileUserRolesStore userRolesStore) {
        super(FileRealmSettings.TYPE, config);
        this.userPasswdStore = userPasswdStore;
        userPasswdStore.addListener(this::expireAll);
        this.userRolesStore = userRolesStore;
        userRolesStore.addListener(this::expireAll);
    }

    @Override
    protected void doAuthenticate(UsernamePasswordToken token, ActionListener<AuthenticationResult> listener) {
        final AuthenticationResult result = userPasswdStore.verifyPassword(token.principal(), token.credentials(), () -> {
            String[] roles = userRolesStore.roles(token.principal());
            return new User(token.principal(), roles);
        });
        listener.onResponse(result);
    }

    @Override
    protected void doLookupUser(String username, ActionListener<User> listener) {
        if (userPasswdStore.userExists(username)) {
            String[] roles = userRolesStore.roles(username);
            listener.onResponse(new User(username, roles));
        } else {
            listener.onResponse(null);
        }
    }

    @Override
    public Map<String, Object> usageStats() {
        Map<String, Object> stats = super.usageStats();
        // here we can determine the size based on the in mem user store
        stats.put("size", userPasswdStore.usersCount());
        return stats;
    }

}
