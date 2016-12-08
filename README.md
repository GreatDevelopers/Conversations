# sunehaG

sunehaG: sunehaG is an official android based open source Instant Messaging Application for GNDEC college

[![Google Play](/google-play-badge.png)](https://play.google.com/store/apps/details?id=in.gndec.sunehag)

![screenshots](https://raw.githubusercontent.com/siacs/Conversations/master/screenshots.png)

##Origins
SunehaG is a FORK of "[Conversations](https://github.com/siacs/Conversations)", an awesome XMPP client for android with great features.

## Design principles

* Be as beautiful and easy to use as possible without sacrificing security or
  privacy
* Rely on existing, well established protocols (XMPP)
* Do not require a Google Account or Phone Number.
* Require as few permissions as possible

## Features

* End-to-end encryption with [OTR](https://otr.cypherpunks.ca/)
* Send and receive images as well as other kind of files
* Indication when your contact has read your message
* Intuitive UI that follows Android Design guidelines
* Pictures / Avatars for your Contacts
* Syncs with web client
* Groups (for multi user conversation)
* Address book integration
* Multiple accounts / unified inbox
* Very low impact on battery life
* Share contact details via QR code


### XMPP Features

sunehaG works with GNDEC's our instant messaging server. It uses XMPP, XMPP is an
extensible protocol. These extensions are standardized as well in so called
XEP's. sunehaG supports a couple of these to make the overall user
experience better.
* [XEP-0065: SOCKS5 Bytestreams](http://xmpp.org/extensions/xep-0065.html) (or mod_proxy65). Will be used to transfer
  files if both parties are behind a firewall (NAT).
* [XEP-0163: Personal Eventing Protocol](http://xmpp.org/extensions/xep-0163.html) for avatars and OMEMO.
* [XEP-0191: Blocking command](http://xmpp.org/extensions/xep-0191.html) lets you blacklist spammers or block contacts
  without removing them from your roster.
* [XEP-0198: Stream Management](http://xmpp.org/extensions/xep-0198.html) allows XMPP to survive small network outages and
  changes of the underlying TCP connection.
* [XEP-0280: Message Carbons](http://xmpp.org/extensions/xep-0280.html) which automatically syncs the messages you send to
  your desktop client and thus allows you to switch seamlessly from your mobile
  client to your desktop client and back within one conversation.
* [XEP-0237: Roster Versioning](http://xmpp.org/extensions/xep-0237.html) mainly to save bandwidth on poor mobile connections
* [XEP-0313: Message Archive Management](http://xmpp.org/extensions/xep-0313.html) synchronize message history with the
  server. Catch up with messages that were sent while Conversations was
  offline.
* [XEP-0352: Client State Indication](http://xmpp.org/extensions/xep-0352.html) lets the server know whether or not
  Conversations is in the background. Allows the server to save bandwidth by
  withholding unimportant packages.
* [XEP-0363: HTTP File Upload](http://xmpp.org/extensions/xep-0363.html) allows you to share files in groups
  and with offline contacts.

## Team

####Head of Development

	Dr. H.S Rai

Code Contributions

   * [Amarjeet Kapoor](https://github.com/amarjeetkapoor1)
   * [Jugraj Singh (PRs)](https://github.com/jugrajsingh)

####Logo

   * [Amitoj Singh (Progress)](https://github.com/amitojsingh)
   * [Gauravjeet Singh (Original)](https://github.com/GauravjeetSingh)

#### I found a bug

Please report it to our [discussion form][discussion]. If your app crashes please
provide a stack trace. If you are experiencing misbehavior please provide
detailed steps to reproduce. Always mention whether you are running the latest
Play Store version or the current HEAD. If you are having problems connecting to
your XMPP server your file transfer doesn’t work as expected please always
include a logcat debug output with your issue (see above).

[discussion]: https://ISSUE_URL
