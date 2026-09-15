package com.rutta.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

private data class Order(val id: Long, val address: String, val phone: String, val client: String, val amount: Int, val delivered: Boolean = false)

class MainActivity : ComponentActivity() {
    private val orders = mutableStateListOf<Order>()
    private var nextId = 1L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainActivityHolder.context = this
        setContent { RuttaApp(orders, onAdd = { o -> orders.add(o.copy(id = nextId++)) }, onToggle = { id -> val i=orders.indexOfFirst{it.id==id}; if(i>=0) orders[i]=orders[i].copy(delivered=!orders[i].delivered) }, onDelete={id->orders.removeAll{it.id==id}}) }
    }
}

@Composable
fun RuttaApp(orders: MutableList<Order>, onAdd:(Order)->Unit, onToggle:(Long)->Unit, onDelete:(Long)->Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = { NavigationBar {
        NavItem("Comanda", Icons.Default.CameraAlt, tab==0){tab=0}
        NavItem("Pedidos", Icons.Default.ReceiptLong, tab==1){tab=1}
        NavItem("Ganancias", Icons.Default.AttachMoney, tab==2){tab=2}
        NavItem("Gastos", Icons.Default.Payments, tab==3){tab=3}
    }}) { pad -> Box(Modifier.fillMaxSize().padding(pad)) {
        when(tab){
            0 -> ComandaScreen(onAdd)
            1 -> OrdersScreen(orders,onToggle,onDelete)
            2 -> EarningsScreen(orders)
            3 -> ExpensesScreen()
        }
    }}
}

@Composable private fun RowScope.NavItem(label:String, icon:androidx.compose.ui.graphics.vector.ImageVector, selected:Boolean, onClick:()->Unit){ NavigationBarItem(selected=selected,onClick=onClick,icon={Icon(icon,null)},label={Text(label, fontSize=11.sp)}) }

@Composable
fun ComandaScreen(onAdd:(Order)->Unit){
    var showCamera by remember{mutableStateOf(false)}
    var address by remember{mutableStateOf("")}; var phone by remember{mutableStateOf("")}; var client by remember{mutableStateOf("")}; var amount by remember{mutableStateOf("")}; var ocr by remember{mutableStateOf("")}
    if(showCamera){ CameraCapture(onText={text->
        ocr=text; val parsed=parseTicket(text); address=parsed.address.ifBlank{address}; phone=parsed.phone.ifBlank{phone}; client=parsed.client.ifBlank{client}; amount=parsed.amount.ifBlank{amount}; showCamera=false
    }, onClose={showCamera=false}); return }
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxSize()){
        item{ Text("RUTTA",fontSize=28.sp,fontWeight=FontWeight.Bold); Text("Nueva comanda",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item{ Button(onClick={showCamera=true},modifier=Modifier.fillMaxWidth().height(64.dp),shape=RoundedCornerShape(18.dp)){Icon(Icons.Default.CameraAlt,null);Spacer(Modifier.width(10.dp));Text("ESCANEAR COMANDA",fontSize=17.sp,fontWeight=FontWeight.SemiBold)} }
        item{ OutlinedButton(onClick={},modifier=Modifier.fillMaxWidth().height(50.dp),shape=RoundedCornerShape(14.dp)){Icon(Icons.Default.Edit,null);Spacer(Modifier.width(8.dp));Text("Ingresar manualmente")} }
        item{ SectionTitle("Datos detectados / editables") }
        item{ Field("Dirección",address){address=it} }
        item{ Field("Teléfono",phone){phone=it} }
        item{ Field("Cliente",client){client=it} }
        item{ Field("Monto",amount){amount=it} }
        item{ if(ocr.isNotBlank()) Text("Texto leído: $ocr",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        item{ Button(onClick={onAdd(Order(0,address,phone,client,amount.filter{it.isDigit()}.toIntOrNull()?:0))},enabled=address.isNotBlank()||phone.isNotBlank(),modifier=Modifier.fillMaxWidth().height(56.dp)){Text("Guardar pedido",fontSize=16.sp)} }
    }
}

@Composable private fun Field(label:String,value:String,onChange:(String)->Unit){ OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(14.dp)) }
@Composable private fun SectionTitle(t:String){Text(t,fontWeight=FontWeight.SemiBold,fontSize=18.sp)}

private data class Parsed(val address:String="",val phone:String="",val client:String="",val amount:String="")
private fun parseTicket(raw:String):Parsed{
    val t=raw.replace("\n"," ").replace(Regex("\\s+")," ").trim()
    val phone=Regex("(?<!\\d)(?:\\+?56\\s*)?(?:9\\s*)?\\d(?:[\\s-]?\\d){7,8}(?!\\d)").find(t)?.value?.replace(Regex("\\D"),"")?.takeLast(9).orEmpty()
    val amount=Regex("(?i)(?:\\$|monto|total)\\s*([0-9][0-9.]{2,})").find(t)?.groupValues?.getOrNull(1)?.replace(".","") ?: Regex("(?<!\\d)([0-9]{3,6})(?!\\d)").findAll(t).lastOrNull()?.groupValues?.get(1).orEmpty()
    val address=Regex("(?i)\\b((?:AV(?:ENIDA)?|PSJ|PJE|PASAJE|CALLE)\\.?\\s+[A-ZÁÉÍÓÚÑ0-9 .'-]{4,60})").find(t)?.groupValues?.get(1)?.trim().orEmpty()
        .replace(Regex("(?i)^AV\\.?\\s"),"Avenida ").replace(Regex("(?i)^PSJ\\.?\\s|^PJE\\.?\\s"),"Pasaje ").replace(Regex("(?i)^CALLE\\s"),"Calle ")
    return Parsed(address,phone,"",amount)
}

@Composable fun CameraCapture(onText:(String)->Unit,onClose:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current; val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){hasPermission=it}
    var imageCapture by remember{mutableStateOf<ImageCapture?>(null)}; val executor=remember{Executors.newSingleThreadExecutor()}
    Box(Modifier.fillMaxSize()){
        if(hasPermission){ AndroidView(factory={ctx->
            val view=PreviewView(ctx); val providerFuture=ProcessCameraProvider.getInstance(ctx); providerFuture.addListener({
                val provider=providerFuture.get(); val preview=Preview.Builder().build().also{it.surfaceProvider=view.surfaceProvider}; val cap=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build(); imageCapture=cap
                provider.unbindAll(); provider.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,cap)
            },ContextCompat.getMainExecutor(ctx)); view
        },modifier=Modifier.fillMaxSize())
        } else { LaunchedEffect(Unit){launcher.launch(Manifest.permission.CAMERA)}; Text("Se necesita permiso de cámara",Modifier.align(Alignment.Center)) }
        Row(Modifier.align(Alignment.BottomCenter).padding(24.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically){
            OutlinedButton(onClick=onClose){Text("Cancelar")}
            FloatingActionButton(onClick={ imageCapture?.let{cap-> val file=java.io.File(context.cacheDir,"rutta_${System.currentTimeMillis()}.jpg"); val opts=ImageCapture.OutputFileOptions.Builder(file).build(); cap.takePicture(opts,executor,object:ImageCapture.OnImageSavedCallback{override fun onError(e:ImageCaptureException){}; override fun onImageSaved(r:ImageCapture.OutputFileResults){ val img=InputImage.fromFilePath(context,Uri.fromFile(file)); TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).addOnSuccessListener{onText(it.text)}.addOnFailureListener{onText("")}}}) }}){Icon(Icons.Default.CameraAlt,null)}
        }
        Text("Enfoca la comanda completa y evita sombras",Modifier.align(Alignment.TopCenter).padding(24.dp),color=MaterialTheme.colorScheme.onPrimary, fontWeight=FontWeight.SemiBold)
    }
}

@Composable fun OrdersScreen(orders:List<Order>,onToggle:(Long)->Unit,onDelete:(Long)->Unit){
    var route by remember{mutableStateOf(false)}
    if(route){RouteScreen(orders,onBack={route=false});return}
    LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Pedidos",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Hoy",color=MaterialTheme.colorScheme.onSurfaceVariant)};item{Button(onClick={route=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Map,null);Spacer(Modifier.width(8.dp));Text("Ruta del día")}};items(orders){o->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(o.address.ifBlank{"Sin dirección"},fontWeight=FontWeight.SemiBold);if(o.phone.isNotBlank())Text(o.phone);Text("$${o.amount}",fontWeight=FontWeight.Bold);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={onToggle(o.id)}){Text(if(o.delivered)"Entregado ✓" else "Marcar entregado")};TextButton(onClick={onDelete(o.id)}){Text("Eliminar")}}}}}}
}

@Composable fun RouteScreen(orders:List<Order>,onBack:()->Unit){Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text("Ruta del día",fontSize=25.sp,fontWeight=FontWeight.Bold)};Card(Modifier.fillMaxWidth().height(220.dp)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("Mapa de entregas\n\nLas direcciones se abrirán en Maps para crear la ruta",textAlign=androidx.compose.ui.text.style.TextAlign.Center)}};orders.forEachIndexed{idx,o->ListItem(headlineContent={Text("${idx+1}. ${o.address.ifBlank{"Sin dirección"}}")},supportingContent={Text(if(o.delivered)"Entregado" else "Pendiente")},leadingContent={Icon(Icons.Default.LocationOn,null)},trailingContent={TextButton(onClick={val u=Uri.parse("https://www.google.com/maps/search/?api=1&query="+Uri.encode(o.address)); MainActivityHolder.context?.startActivity(Intent(Intent.ACTION_VIEW,u))}){Text("Abrir")}})}}
}
private object MainActivityHolder{var context:android.content.Context?=null}

@Composable fun EarningsScreen(orders:List<Order>){val total=orders.sumOf{it.amount};Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){Text("Ganancias",fontSize=28.sp,fontWeight=FontWeight.Bold);Stat("Ingresos de hoy","$${total}");Stat("Pedidos",orders.size.toString());Stat("Entregados",orders.count{it.delivered}.toString())}}
@Composable fun Stat(a:String,b:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp)){Text(a,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(b,fontSize=28.sp,fontWeight=FontWeight.Bold)}}}
@Composable fun ExpensesScreen(){var amount by remember{mutableStateOf("")};var note by remember{mutableStateOf("")};Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("Gastos",fontSize=28.sp,fontWeight=FontWeight.Bold);Text("Registra gasolina y otros gastos por separado.",color=MaterialTheme.colorScheme.onSurfaceVariant);Field("Monto",amount){amount=it};Field("Descripción",note){note=it};Button(onClick={},modifier=Modifier.fillMaxWidth()){Text("Agregar gasto")}}}
