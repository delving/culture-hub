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
