# TODO

## QS stage exploratory findings (2026-04-04)

- [ ] Fix dead `notifyOnVote` option on step 1.
  Live verification on `https://qs.woodle.click/`: a poll created with only `Bei jeder neuen Umfrage eine E-Mail erhalten` enabled did not send any follow-up mail after a participant vote; only the poll-created mail was delivered to `supporting990@sharebot.net`.
  Relevant code: [src/main/resources/static/poll/new-step1.html](src/main/resources/static/poll/new-step1.html), [src/main/resources/templates/poll/new-step1.html](src/main/resources/templates/poll/new-step1.html), [src/main/java/io/github/bodote/woodle/adapter/in/web/PollNewPageController.java](src/main/java/io/github/bodote/woodle/adapter/in/web/PollNewPageController.java), [src/main/java/io/github/bodote/woodle/adapter/in/web/PollSubmitController.java](src/main/java/io/github/bodote/woodle/adapter/in/web/PollSubmitController.java).

- [ ] Align notification copy with actual behavior.
  The first checkbox promises `Bei jeder neuen Umfrage eine E-Mail erhalten`, but the implemented and observed behavior is only `Umfrage erstellt` plus `Neuer Eintrag` notifications. Users are currently offered an option that does not map to a distinct reachable behavior.

- [ ] Fix typo on the static poll loader page.
  Live `/poll/static/...` loader renders `Bitte noch ein bischen Geduld, wir laden gerade die Umfrage`.
  `bischen` should be `bisschen`.
  Relevant code: [src/main/resources/templates/poll/static-loader.html](src/main/resources/templates/poll/static-loader.html), [src/main/resources/static/poll/static/loader.html](src/main/resources/static/poll/static/loader.html).

- [ ] Unify email language with the German UI.
  The stage UI is German and email subjects are German, but the poll-created email body is English (`Hello ... your poll ... has been created successfully.`).
  Relevant code: [src/main/java/io/github/bodote/woodle/adapter/out/email/SesPollEmailSender.java](src/main/java/io/github/bodote/woodle/adapter/out/email/SesPollEmailSender.java), [src/main/java/io/github/bodote/woodle/adapter/out/email/SmtpPollEmailSender.java](src/main/java/io/github/bodote/woodle/adapter/out/email/SmtpPollEmailSender.java).
