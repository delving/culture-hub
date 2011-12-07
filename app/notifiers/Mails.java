/*
 * Copyright 2011 Delving B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package notifiers;

import models.User;
import play.mvc.Mailer;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class Mails extends Mailer {

    public static void activation(User user, String activationToken) {
        setSubject("Welcome to CultureHub");
        addRecipient(user.email());
        ThemeAwareBridge.before();
        try {
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        send(user, activationToken);
    }

    public static void resetPassword(User user, String resetPasswordToken) {
        setSubject("Reset your password");
        addRecipient(user.email());
        ThemeAwareBridge.before();
        try {
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        send(user, resetPasswordToken);
    }

    public static void reportError(String subject, String report) {
        ThemeAwareBridge.before();
        try {
            setSubject(subject);
            addRecipient(ThemeAwareBridge.theme().emailTarget().exceptionTo());
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        send(report);

    }

}
