package thederpgamer.combattweaks.commands;

import api.mod.StarMod;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;
import thederpgamer.combattweaks.CombatTweaks;

import javax.annotation.Nullable;

/**
 * Runs unit tests for Combat Tweaks.
 */
public class RunTestsCommand implements CommandInterface {
	@Override
	public String getCommand() {
		return "ct_run_tests";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"ct_run_tests"};
	}

	@Override
	public String getDescription() {
		return "Runs internal tests for Combat Tweaks.";
	}

	@Override
	public boolean isAdminOnly() {
		return true;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		//Run unit tests

		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {

	}

	@Override
	public StarMod getMod() {
		return CombatTweaks.getInstance();
	}
}
