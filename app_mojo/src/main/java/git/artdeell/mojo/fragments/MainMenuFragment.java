package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.R;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super();
    }


    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private android.graphics.drawable.GradientDrawable surface(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(20)); d.setStroke(dp(1),0xFF29362F); return d;
    }
    private android.widget.TextView text(String value, int size, int color) {
        android.widget.TextView t = new android.widget.TextView(requireContext());
        t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private void addButton(android.widget.LinearLayout target, int id, String label, boolean primary) {
        Button b = new Button(requireContext()); b.setId(id); b.setText(label);
        b.setAllCaps(false); b.setTextSize(16);
        b.setTextColor(primary ? 0xFF042016 : 0xFFECF5EF);
        b.setBackgroundTintList(null);
        b.setBackground(surface(primary ? 0xFF35D6A0 : 0xFF14221B));
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(-1,dp(56));
        p.topMargin=dp(10); target.addView(b,p);
    }
    @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable android.view.ViewGroup container, @Nullable Bundle state) {
        Context c = requireContext();
        android.widget.ScrollView scroll = new android.widget.ScrollView(c);
        scroll.setFillViewport(true); scroll.setBackgroundColor(0xFF060C09);
        android.widget.LinearLayout body = new android.widget.LinearLayout(c);
        body.setOrientation(android.widget.LinearLayout.VERTICAL);
        body.setPadding(dp(20),dp(24),dp(20),dp(28)); scroll.addView(body);
        android.widget.TextView title = text("CUBO",30,0xFFF0F8F3);
        title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        title.setLetterSpacing(0.16f); body.addView(title);
        body.addView(text("Tu mundo, a tu manera.",14,0xFF9AAEA2));
        android.widget.LinearLayout card = new android.widget.LinearLayout(c);
        card.setOrientation(android.widget.LinearLayout.VERTICAL);
        card.setPadding(dp(18),dp(20),dp(18),dp(20)); card.setBackground(surface(0xFF101A15));
        android.widget.LinearLayout.LayoutParams cp = new android.widget.LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(28); body.addView(card,cp);
        card.addView(text("MINECRAFT JAVA",12,0xFF35D6A0));
        android.widget.TextView heading = text("Listo para tu próximo mundo",22,0xFFF0F8F3);
        heading.setPadding(0,dp(10),0,dp(12)); card.addView(heading);
        card.addView(text("Selecciona una versión. Añade tu cuenta desde la barra superior cuando quieras jugar.",14,0xFF9AAEA2));
        mcVersionSpinner versions = new mcVersionSpinner(c);
        versions.setId(R.id.mc_version_spinner);
        android.widget.LinearLayout.LayoutParams vp = new android.widget.LinearLayout.LayoutParams(-1,dp(64));
        vp.topMargin=dp(12); card.addView(versions,vp);
        ImageButton edit = new ImageButton(c); edit.setId(R.id.edit_profile_button);
        edit.setImageResource(R.drawable.ic_px_edit); edit.setContentDescription("Editar versión o instancia");
        edit.setBackground(surface(0xFF14221B));
        card.addView(edit,new android.widget.LinearLayout.LayoutParams(dp(48),dp(48)));
        addButton(card,R.id.play_button,"Jugar",true);
        android.widget.TextView tools = text("HERRAMIENTAS",12,0xFF35D6A0);
        tools.setPadding(0,dp(28),0,dp(4)); body.addView(tools);
        addButton(body,R.id.custom_control_button,"Controles táctiles",false);
        addButton(body,R.id.open_files_button,"Archivos de la instancia",false);
        addButton(body,R.id.install_jar_button,"Ejecutar instalador .jar",false);
        addButton(body,R.id.share_logs_button,"Compartir registros",false);
        addButton(body,R.id.news_button,"Ayuda de Mojo (proyecto base)",false);
        addButton(body,R.id.social_media_button,"Comunidad del proyecto base",false);
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
