package com.example.aumentedreality

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.aumentedreality.ui.theme.Translucent
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView

class MainActivity: ComponentActivity() {
    @SuppressLint("UnrememberedMutableState")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    var currentModel by
                        mutableStateOf(Food("wood.glb", R.drawable.burger))

                    ARScreen(currentModel = currentModel)
                    Menu(modifier = Modifier.align(Alignment.BottomCenter)) {
                        currentModel = it
                    }
                }
            }
        }
    }
}

@Composable
fun Menu(modifier: Modifier,onClick:(Food)->Unit) {
    var currentIndex by remember {
        mutableIntStateOf(0)
    }

    val itemsList = listOf(
        Food("burger.glb", R.drawable.burger),
        Food("instant.glb",R.drawable.instant),
        Food("momos.glb",R.drawable.momos),
        Food("pizza.glb",R.drawable.pizza),
        Food("ramen.glb",R.drawable.ramen),

        )
    fun updateIndex(offset:Int){
        currentIndex = (currentIndex+offset + itemsList.size) % itemsList.size
        onClick(itemsList[currentIndex])
    }
    Row(modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        IconButton(onClick = {
            updateIndex(-1)
        }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_back_ios_24), contentDescription ="previous" )
        }

        CircularImage(imageId = itemsList[currentIndex].imageId )

        IconButton(onClick = {
            updateIndex(1)
        }) {
            Icon(painter = painterResource(id = R.drawable.baseline_arrow_forward_ios_24), contentDescription ="next")
        }
    }
}

@Composable
fun CircularImage(
    modifier: Modifier=Modifier,
    imageId: Int
) {
    Box(modifier = modifier
        .size(140.dp)
        .clip(CircleShape)
        .border(width = 3.dp, Translucent, CircleShape)
    ){
        Image(painter = painterResource(id = imageId), contentDescription = null, modifier = Modifier.size(140.dp), contentScale = ContentScale.FillBounds)
    }
}

@Composable
fun ARScreen(currentModel: Food) {

    var frame by remember {
        mutableStateOf<Frame?>(null)
    }

    val nodes = rememberNodes()

    val modelNode = remember {
        mutableStateOf<ModelNode?>(null)
    }

    val engine = rememberEngine()

    val modelLoader = rememberModelLoader(engine = engine)

    val materialLoader = rememberMaterialLoader(engine = engine)

    val cameraNode = rememberARCameraNode(engine = engine)

    val view = rememberView(engine = engine)

    val planeRenderer by remember {
        mutableStateOf(true)
    }
    
    val collisionSystem = rememberCollisionSystem(view = view)

    Box(modifier = Modifier.fillMaxSize()) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = nodes,
            modelLoader = modelLoader,
            planeRenderer = planeRenderer,
            engine = engine,
            view = view,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            sessionConfiguration = { session, config ->
                config.depthMode = when(session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    true -> Config.DepthMode.AUTOMATIC
                    else -> Config.DepthMode.DISABLED
                }
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            },
            onViewCreated = {
                this.planeRenderer.isShadowReceiver = false
                this.modelLoader.loadModelAsync(
                    "models/${currentModel.name}",
                    onResult = {
                        if (it != null) {
                            modelNode.value = ModelNode(it.instance)
                        }
                    }
                )
            },
            onSessionUpdated = { session, updateFrame ->
                frame = updateFrame
                if (nodes.isEmpty()) {
                    updateFrame.getUpdatedPlanes()
                        .firstOrNull() { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                        ?.let { it.createAnchorOrNull(it.centerPose) }?.let { anchor ->
                            nodes += createAnchorNode(
                                engine = engine,
                                modelLoader = modelLoader,
                                materialLoader = materialLoader,
                                anchor = anchor,
                                currentModel = currentModel
                            )
                        }
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    if(node == null) {
                        val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                        hitResults?.firstOrNull() {
                            it.isValid(depthPoint = false, point = false)
                        }?.createAnchorOrNull()?.let { anchor ->
                            println("SE HIZO UN TAP")
                            nodes.clear()
                            nodes += createAnchorNode(
                                engine = engine,
                                modelLoader = modelLoader,
                                materialLoader = materialLoader,
                                anchor = anchor,
                                currentModel = currentModel
                            )
                        }
                    }
                }
            )
        )
    }
}

fun createAnchorNode(
    engine: Engine,
    modelLoader: ModelLoader,
    materialLoader: MaterialLoader,
    anchor: Anchor,
    currentModel: Food
): AnchorNode {
    val anchorNode = AnchorNode(engine, anchor)
    val modelNode = ModelNode(
        modelInstance = modelLoader.createInstancedModel("models/${currentModel.name}",1).single(),
    ).apply {
        isEditable = true
    }
    val boundingBoxNode = CubeNode(
        engine,
        center = modelNode.center,
        materialInstance = materialLoader.createColorInstance(Color.White)
    ).apply {
        isVisible = false
    }
    modelNode.addChildNode(boundingBoxNode)
    anchorNode.addChildNode(modelNode)
    listOf(modelNode, anchorNode).forEach {
        it.onEditingChanged = {editingTransforms ->
            boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
        }
    }
    return anchorNode
}


data class Food(var name:String,var imageId:Int)