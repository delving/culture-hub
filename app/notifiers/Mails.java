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
import play.Play;
import play.i18n.Messages;
import play.libs.IO;
import play.mvc.Mailer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Manuel Bernhardt <bernhardt.manuel@gmail.com>
 */
public class Mails extends Mailer {
    
    private static List<String> quotes;

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

    public static void accountBlocked(User user, String contactEmail) {
        setSubject(Messages.get("mail.subject.accountblocked"));
        addRecipient(user.email());
        ThemeAwareBridge.before();
        try {
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        String userName = user.userName();
        send(userName, contactEmail);
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
        String quote = getRandomQuote();
        send(report, quote);
    }

    public static void newUser(String subject, String hub, String userName, String fullName, String email) {
        ThemeAwareBridge.before();
        try {
            setSubject(subject);
            addRecipient(ThemeAwareBridge.theme().emailTarget().exceptionTo());
            setFrom(ThemeAwareBridge.theme().emailTarget().systemFrom());
        } finally {
            ThemeAwareBridge.after();
        }
        String quote = getRandomQuote();
        send(fullName, hub, userName, email, quote);
    }

    private static String getRandomQuote() {
        if(quotes == null) {
            // quotes.txt courtesy of Rudy Velthuis - http://blogs.teamb.com/rudyvelthuis/2006/07/29/26308
            File f = Play.getFile("/app/views/Mails/quotes.txt");
            quotes = new ArrayList<String>();
            List<String> lines = IO.readLines(f);
            StringBuffer sb = new StringBuffer();
            for(String line : lines) {
                if(line.equals(".")) {
                    quotes.add(sb.toString());
                    sb = new StringBuffer();
                } else {
                    sb.append(line).append("\n");
                }
            }
        }
        int index = (int)(Math.random() * quotes.size() + 1);
        return quotes.get(index);
    }

}
