package com.sakayori.music.ui.screen.other

import androidx.compose.foundation.Image
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sakayori.music.expect.openUrl
import com.sakayori.music.ui.component.RippleIconButton
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.utils.VersionManager
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun CreditScreen(
    paddingValues: PaddingValues,
    navController: NavController,
) {
    val isBlurEnabled = com.sakayori.music.extension.LocalBlurEnabled.current
    val hazeState = rememberHazeState(blurEnabled = isBlurEnabled)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 64.dp)
                .verticalScroll(rememberScrollState())
                .hazeSource(state = hazeState),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Image(
            painter = painterResource(Res.drawable.app_icon),
            contentDescription = "App Icon",
            modifier =
                Modifier
                    .size(150.dp)
                    .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = stringResource(Res.string.app_name),
            style = typo().titleLarge,
            fontSize = 22.sp,
        )

        Text(
            text = stringResource(Res.string.version_format, VersionManager.getVersionName()),
            style = typo().bodySmall,
            fontSize = 13.sp,
        )

        Text(
            text = "Running on ${com.sakayori.music.getPlatform().osName()}",
            style = typo().bodySmall,
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
        )

        Text(
            text = stringResource(Res.string.sakayori_dev),
            style = typo().bodyMedium,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(Res.string.credit_app),
            style = typo().bodyMedium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable {
                    openUrl("https://github.com/maxrave-dev/SimpMusic")
                }
                .padding(12.dp),
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.forked_from),
                    style = typo().bodySmall,
                    color = Color(0xFF00BCD4),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SimpMusic by maxrave-dev",
                    style = typo().bodyMedium,
                    color = Color.White,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(Res.string.team),
            style = typo().labelMedium,
            color = Color(0xFF00BCD4),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp, vertical = 4.dp),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            TeamMember(
                username = "Sakayorii",
                role = stringResource(Res.string.lead_maintainer),
            )
            TeamMember(
                username = "Lammk",
                role = stringResource(Res.string.co_maintainer),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            TextButton(
                onClick = {
                    openUrl("https://music.sakayori.dev")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.website))
            }

            TextButton(
                onClick = {
                    openUrl("https://github.com/Sakayorii/sakayori-music")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.github))
            }

            TextButton(
                onClick = {
                    openUrl("https://github.com/Sakayorii/sakayori-music/issues")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.issue_tracker))
            }

            TextButton(
                onClick = {
                    openUrl("https://ko-fi.com/sakayori")
                },
                modifier =
                    Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = 25.dp)
                        .defaultMinSize(minHeight = 1.dp, minWidth = 1.dp),
            ) {
                Text(text = stringResource(Res.string.buy_me_a_coffee))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(Res.string.copyright),
            style = typo().bodySmall,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 25.dp, vertical = 5.dp),
            textAlign = TextAlign.Start,
        )

        Spacer(modifier = Modifier.height(200.dp))
    }
    TopAppBar(
        modifier =
            Modifier
                .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                    blurEnabled = isBlurEnabled
                },
        title = {
            Text(
                text = stringResource(Res.string.app_name),
                style = typo().titleMedium,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(
                            align = Alignment.CenterVertically,
                        ).basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
        },
        navigationIcon = {
            Box(Modifier.padding(horizontal = 5.dp)) {
                RippleIconButton(
                    Res.drawable.baseline_arrow_back_ios_new_24,
                    Modifier
                        .size(32.dp),
                    true,
                ) {
                    navController.navigateUp()
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                Color.Transparent,
                Color.Unspecified,
                Color.Unspecified,
                Color.Unspecified,
                Color.Unspecified,
            ),
    )
}

@Composable
private fun TeamMember(
    username: String,
    role: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            openUrl("https://github.com/$username")
        },
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data("https://github.com/$username.png")
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .diskCacheKey("github-$username")
                    .crossfade(300)
                    .build(),
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(CircleShape),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = username,
            style = typo().bodyMedium,
        )
        Text(
            text = role,
            style = typo().bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}
