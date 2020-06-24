var elmnt=null;
document.write();
var alrt;
function showDialog(data){
	if(elmnt==null){
		if(data.element)
			elmnt=data.element;
		else
			elmnt=document.body;
		elmnt.innerHTML=elmnt.innerHTML+'<div class="alertbox"></div>';
		alrt=document.getElementsByClassName('alertbox')[0];
		alrt.innerHTML='<div class="alertbox-container">\
				<svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130.2 130.2" id="successful">\
				  <circle class="path circle" fill="none" stroke="#73AF55" stroke-width="6" stroke-miterlimit="10" cx="65.1" cy="65.1" r="62.1"/>\
				  <polyline class="path check" fill="none" stroke="#73AF55" stroke-width="6" stroke-linecap="round" stroke-miterlimit="10" points="100.2,40.2 51.5,88.8 29.8,67.5 "/>\
				</svg>\
				<svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130.2 130.2" id="errorful">\
				  <circle class="path circle" fill="none" stroke="#D06079" stroke-width="6" stroke-miterlimit="10" cx="65.1" cy="65.1" r="62.1"/>\
				  <line class="path line" fill="none" stroke="#D06079" stroke-width="6" stroke-linecap="round" stroke-miterlimit="10" x1="34.4" y1="37.9" x2="95.8" y2="92.3"/>\
				  <line class="path line" fill="none" stroke="#D06079" stroke-width="6" stroke-linecap="round" stroke-miterlimit="10" x1="95.8" y1="38" x2="34.4" y2="92.2"/>\
				</svg>\
				<p id="alertbox-text"></p>\
				<button id="alertbox-button">Ok</button>\
			</div>';
	}
	alrt.style.display="block";
	var btn=document.getElementById('alertbox-button');
	if(data.type){
		if(data.type=='success'){
			document.getElementById('successful').style.display="block";
		}
		else if(data.type=='error'){
			document.getElementById('errorful').style.display="block";
			// btn.style.backgroundColor='#D06079';
		}
	}
	if(data.color){
		btn.style.backgroundColor=data.color;
	}else if(data.type){
		if(data.type=='success'){
			btn.style.backgroundColor='#73AF55';
		}
		else if(data.type=='error'){
			btn.style.backgroundColor='#D06079';
		}
	}
	if(data.button){
		document.getElementById('alertbox-button').innerHTML=data.button;
	}
	if(data.message){
		document.getElementById('alertbox-text').innerHTML=data.message;
	}
	btn.onclick=function(){
		alrt.style.display="none";
		if(data.onclick && typeof(data.onclick)=="function"){
			data.onclick();
		}
	}
}