package openshock.integrations.minecraft

import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import openshock.integrations.minecraft.config.AccountConfig
import openshock.integrations.minecraft.platform.McCompat

/**
 * Where the API token is typed, on a screen of its own.
 *
 * The token is the one secret in the config - anyone holding it can shock whoever it belongs to -
 * and the settings screen is the thing most likely to be on camera while somebody is being talked
 * through the setup. So it is not on that screen at all: Setup says whether a token is set and
 * nothing more, and this is the only place one is ever entered.
 *
 * Which also means there is nothing to mask. The box starts **empty** rather than pre-filled with
 * what is already stored, so the stored token is never rendered anywhere, by anything. That is the
 * whole design, and it is why this replaced an attempt at drawing asterisks over YACL's own text
 * field: that meant fighting the widget's caret and scroll arithmetic on three different YACL
 * versions, for a weaker result than simply not drawing the thing.
 *
 * An empty box cannot be saved - Save stays greyed out until something is typed - so opening this
 * and pressing the obvious button cannot wipe a working token. Clearing is its own button.
 *
 * ### Versions
 *
 * Built out of [Button] and [EditBox] and nothing else, for the same reason [RemoteScreen] is
 * built out of buttons: both are spelled identically from 1.20.4 to 26.2, and a screen that only
 * adds widgets and never draws anything itself needs no per-version handling at all. Anything that
 * paints - a title, a label, a background - would have to follow Minecraft's rendering rewrites.
 * Hence the captions below being buttons nobody can press.
 */
class ApiTokenScreen(private val parent: Screen?) : Screen(Component.literal("API Token")) {

    /** Longer than any token OpenShock issues, and far longer than [EditBox]'s default of 32. */
    private val maxLength = 256

    override fun init() {
        val account = AccountConfig.HANDLER.instance()

        val width = 260
        val height = 20
        val left = (this.width - width) / 2
        var top = this.height / 4

        fun next(): Int {
            val y = top
            top += height + 6
            return y
        }

        // What is already stored, said without saying it.
        caption(
            if (account.apiToken.isBlank()) "No API token set" else "An API token is already set",
            left, next(), width, height,
        )

        caption("Paste a new one below to replace it", left, next(), width, height)

        val box = EditBox(font, left, next(), width, height, Component.literal("API Token"))
        box.setMaxLength(maxLength)
        box.setHint(Component.literal("Paste your API token"))

        val save = Button.builder(Component.literal("Save")) {
            // Trimmed because a token that arrives with a stray space or newline from a copy fails
            // authentication in a way that looks exactly like a wrong token.
            account.apiToken = box.value.trim()
            AccountConfig.HANDLER.save()

            // Back to Setup, which rebuilds - and so notices the new token and goes and fetches the
            // shockers it can reach, which is the next thing anybody wants after setting one.
            back()
        }
            .bounds(left, next(), width, height)
            .build()

        // Nothing typed is not a request to clear the token, it is somebody who opened this and
        // changed their mind. Clearing has its own button precisely so it cannot happen by accident.
        save.active = false
        box.setResponder { typed -> save.active = typed.isNotBlank() }

        addRenderableWidget(box)
        addRenderableWidget(save)

        addRenderableWidget(
            Button.builder(Component.literal("Clear token")) {
                account.apiToken = ""
                AccountConfig.HANDLER.save()
                back()
            }
                .bounds(left, next(), width, height)
                .build()
                .also { it.active = account.apiToken.isNotBlank() }
        )

        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) { back() }
                .bounds(left, next(), width, height)
                .build()
        )

        // So the box takes typing straight away: this screen exists to be pasted into.
        setInitialFocus(box)
    }

    /** Escape leaves the token alone, the same as Cancel. */
    override fun onClose() = back()

    private fun back() = McCompat.setScreen(ConfigScreen.create(parent))

    /** A line of text, as a button nobody can press - see the note on this class about drawing. */
    private fun caption(text: String, x: Int, y: Int, width: Int, height: Int) {
        addRenderableWidget(
            Button.builder(Component.literal(text)) { }
                .bounds(x, y, width, height)
                .build()
                .also { it.active = false }
        )
    }
}
