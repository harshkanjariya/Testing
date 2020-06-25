class HTable{
	constructor(e,data=null){
		if(data!=null)
			this.original=data;
		else
			this.original=[];
		e.innerHTML=`<h1 id="htable-heading">Table</h1>
<div id="htable-filterdiv">
	<div id="htable-entries">
		Show <select class="form-field">
			<option>10</option>
			<option>25</option>
			<option>50</option>
			<option>100</option>
		</select> Entries
	</div>
	<input class="form-field" id="htable-searchbox" type="text" placeholder="Search...">
</div>
<div class="table-responsive">
	<table class="table" id="htable">
		<thead>
			<tr id="htable-head"></tr>
		</thead>
		<tbody id="htable-databody"></tbody>
	</table>
</div>
<div style="text-align: right;">
	<div id="htable-pagignation">
		<span id="htable-pagignation-prev">Previous</span>
		<span id="htable-page-numbers"></span>
		<span id="htable-pagignation-next">Next</span>
	</div>
</div>`;
		this.data=[];
		var elems=e.children;
		var obj=this;
		elems[1].children[1].oninput=function(){
			obj.filter(this.value);
		};
		elems[1].children[0].children[0].onchange=function(){
			obj.setPageCount(this);
		};
		elems[3].children[0].children[0].onclick=function(){
			if(obj.page>0)obj.setPage(obj.page-1);
		};
		elems[3].children[0].children[2].onclick=function(){
			if((obj.page+1)*obj.perpagerows<obj.data.length)obj.setPage(obj.page+1);
		};
		this.heading=elems[0];
		this.head=elems[2].children[0].children[0].children[0];
		this.databody=elems[2].children[0].children[1];
		this.pagenumbers=elems[3].children[0].children[1];
		this.sorted=true;
		this.page=0;
		this.perpagerows=10;
		this.color='#27b842';
		this.filter('',Object.keys(this.original)[0]);
		this.loadHeads(Object.keys(this.original)[0]);
		this.loadPage(0);
		this.loadPageNumbers();
	}
	setData(d){
	    this.original=d;
		this.filter('',Object.keys(this.original)[0]);
		this.loadHeads(Object.keys(this.original)[0]);
		this.loadPage(0);
		this.loadPageNumbers();
	}
	filter(str='',key=null){
		this.data=[];
		str=''+str;
		if(str=='')
			for (var i = this.original.length - 1; i >= 0; i--) {
				this.data.push(this.original[i]);
			}
		else{
			for (var i = 0; i < this.original.length; i++) {
				if(key!=null){
					if(str.indexOf(''+this.original[i][key])>=0)
						this.data.push(this.original[i]);
				}else{
					for(var k in this.original[i]){
						if((''+this.original[i][k]).indexOf(str)>=0){
							this.data.push(this.original[i]);
							break;
						}
					}
				}
			}
		}
		this.loadPage();
		this.setPage(0);
		this.loadPageNumbers();
	}
	setPage(i) {
		this.page=i;
		this.loadPageNumbers();
		this.loadPage();
	}
	setPageCount(e) {
		this.perpagerows=parseInt(e.value);
		this.loadPageNumbers();
		this.loadPage();
	}
	loadPageNumbers(){
		var num=Math.floor(this.data.length/this.perpagerows);
		if(this.data.length%this.perpagerows>0)num++;
		var s='';
		this.pagenumbers.innerHTML="";
		for(var i=0;i<num;i++){
			var spn=document.createElement('span');
			spn.innerHTML=(i+1);
			var obj=this;
			spn.onclick=function(){
				obj.setPage(parseInt(this.innerHTML)-1);
			};
			if(i==this.page)
				spn.style=`background-color:${this.color};color:white;`;
			this.pagenumbers.appendChild(spn);
		}
		this.pagenumbers.style.display=this.data.length>0?"inline-block":"none";
	}
	loadPage(){
		var s='';
		for(var i=0;i<this.perpagerows;i++){
			s+='<tr>';
			for(var k in this.data[i+this.page*this.perpagerows]){
				s+='<td>'+this.data[i+this.page*this.perpagerows][k]+'</td>';
			}
			s+='</tr>';
		}
		this.databody.innerHTML=s;
	}
	loadHeads(key){
		var s='';
		this.head.innerHTML='';
		for(var k in this.data[0]){
			var th=document.createElement('th');
			s=k;
			console.log(key);
			if(k!=key)
			    th.className="asc desc";
			else{
			    if(this.sorted)
			        th.className="asc";
			    else
			        th.className="desc";
			}
			th.innerHTML=s;
			var obj=this;
			th.onclick=function(){
				obj.hsort(this.innerHTML);
			};
			this.head.appendChild(th);
		}
	}
	hsort(key){
		if(typeof(this.sorted)!="boolean" || this.sorted!=true)this.sorted=true;
		else this.sorted=false;
		this.loadHeads(key);
		var srt=this.sorted;
		this.data.sort(function(a,b){
			var x = a[key]; var y = b[key];
			if(srt)
				return ((x < y) ? -1 : ((x > y) ? 1 : 0));
			else
				return ((x > y) ? -1 : ((x > y) ? 1 : 0));
		});
		this.loadPage(this.page);
	}
}
