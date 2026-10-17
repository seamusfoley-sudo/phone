# Spnvoip – Android SIP client

A simple single-account SIP softphone: one dial pad, one call screen, one settings screen.

## Build
1. Open the `Spnvoip` folder in Android Studio (Ladybug or newer).
2. Let Gradle sync (it downloads the Linphone SIP SDK from download.linphone.org).
3. Run on a device (Android 8.0 / API 26+).

## Using it
- First launch opens **Account**: username/extension, password, server (`host` or `host:port`).
- Saving **replaces** any previous account. The engine is wiped before the new account is added,
  so it can only ever register one account.
- The status pill on the dial screen shows Registered / Connecting / Failed.

## Keep-alive and battery
Staying registered is handled so the phone can sleep almost all the time:

| Measure | Why it saves battery |
|---|---|
| TCP transport by default | NAT keeps TCP mappings for minutes, so one tiny CRLF keep-alive every 120 s holds the registration. UDP would need one every ~30 s. |
| REGISTER expiry 3600 s | Full re-registrations happen hourly, not every few minutes. |
| No timers, polling or wake locks in app code | The radio only wakes for the keep-alive packet or an incoming call. |
| Network changes are event-driven | The SIP engine listens to Android connectivity callbacks and re-registers only when the network actually changes. |
| Background mode | When no Spnvoip screen is visible the engine slows its internal housekeeping (`core.enterBackground()`). |
| Video, presence, push off | Nothing else generates traffic. |
| Minimal foreground service | Keeps the process alive with a silent, minimum-priority notification and does no work itself. |

The interval is adjustable under **Account → Advanced** (15–900 s). If calls stop arriving after
the phone has been idle a while, the NAT/firewall in front of the phone is timing out sooner.
Lower the interval a little rather than switching to UDP.

**Fully zero-cost option:** if your PBX/SBC can send push notifications (RFC 8599, e.g. via Flexisip),
the app could drop the persistent connection entirely and wake on push. That needs server-side work
and Firebase, so it isn't included here.

## Licensing note
Linphone SDK is GPLv3. Distributing a closed-source app built on it requires a commercial
licence from Belledonne Communications.

## Code map
- `SipManager.kt` – SIP engine, single account, keep-alive config, call control
- `SipService.kt` – foreground service + notifications (status, incoming call)
- `AccountStore.kt` – stores the one account
- `ui/` – Dial, Call and Settings screens (Jetpack Compose)
