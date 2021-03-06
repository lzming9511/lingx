package com.lingx.core.service.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.lingx.core.model.IEntity;
import com.lingx.core.model.bean.ItemBean;
import com.lingx.core.model.bean.OptionBean;
import com.lingx.core.model.bean.PackBean;
import com.lingx.core.service.IModelService;
import com.lingx.core.service.IPackageService;
import com.lingx.core.service.impl.model.ModelIO;
import com.lingx.core.utils.Utils;
import com.lingx.core.utils.ZipUtils;

/** 
 * @author www.lingx.com
 * @version 创建时间：2015年9月26日 上午1:23:12 
 * 类说明 
 */
@Component(value="lingxPackageService")
public class PackageServiceImpl implements IPackageService {
	public static final Logger log=LogManager.getLogger(PackageServiceImpl.class);
	private String basePath;
	private String uploadURL="http://www.lingx.com/us";
	//private String uploadURL="http://127.0.0.1:8080/site/us";
	//private String uploadURL="http://192.168.1.252:8090/us";
	private String zipPath="/temp/package/";
	private String zipFile="/temp/packageZip.zip";
	@Resource(name="jdbcTemplate")
	private JdbcTemplate jdbcTemplate;
	@Resource
	private IModelService modelService;
	
	public void packAndDownload(PackBean bean,String basePath,OutputStream outputStream){
		try {
			String file=this.pack(bean, basePath);
			FileInputStream fis=new FileInputStream(file);
			byte bytes[]=new byte[1024*4];
			int len=0;
			while((len=fis.read(bytes))!=-1){
				outputStream.write(bytes, 0, len);
			}
			fis.close();
			outputStream.close();
			
			FileUtils.cleanDirectory(new File(this.basePath+"temp"));
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}

	private String pack(PackBean bean,String basePath){
		this.basePath=basePath;
		Map<String,Object> config=new HashMap<String,Object>();
		List<Map<String,Object>> entitys=new ArrayList<Map<String,Object>>();
		this.init(config, bean);
		try {
			File zipPath=new File(this.basePath+this.zipPath);
			if(!zipPath.exists())zipPath.mkdirs();
			
			FileUtils.cleanDirectory(zipPath);
			for(String temp:bean.getFileList()){
				if(Utils.isNull(temp))continue;
				log.info("copy file:"+temp);
				FileUtils.copyFile(new File(this.basePath+temp), new File(this.basePath+this.zipPath+temp));
			}
			
			for(String temp:bean.getModelList()){
				if(Utils.isNull(temp))continue;
				log.info("copy model:"+temp);
				IEntity e=this.modelService.get(temp);
				ModelIO.writeDisk(this.basePath+this.zipPath+"/"+temp+".lingx", e);
				
				entitys.add(this.jdbcTemplate.queryForMap("select * from tlingx_entity where code=?",temp));
			}
			
			if(bean.isOption()){
				log.info("copy option:"+bean.getAppid());
				String optionJSON=getOptionJSON(this.jdbcTemplate,bean.getAppid());
				config.put("optionJSON", optionJSON);
			}
			config.put("entityJSON", JSON.toJSONString(entitys));
			config.put("funcJSON", bean.getFuncJson());

			config.put("model", bean.getModelList());
			config.put("files", bean.getFileList());
			
			FileUtils.writeStringToFile(new File(this.basePath+this.zipPath+"/config.json"), JSON.toJSONString(config),"UTF-8");
			
			String zip=this.zip(zipPath,new File(this.basePath+this.zipFile));
			//this.upload(new URL(this.uploadURL), new File(zip));
			return zip;
		} catch (Exception e) {
			
			e.printStackTrace();
		}
		return "";
	}
	private void addFileListByLingx(File file,List<String> fileList,String basePath){
		if(file.isDirectory()){
			File[] files=file.listFiles();
			for(File f:files){
				this.addFileListByLingx(f, fileList,basePath);
			}
		}else{
			fileList.add(file.getAbsolutePath().replace(basePath, "").replace("\\", "/"));
		}
	}
	private void addEntityListByLingx(List<String> modelList){
		List<Map<String,Object>> list=this.jdbcTemplate.queryForList("select code from tlingx_entity where app_id=?","335ec1fc-1011-11e5-b7ab-74d02b6b5f61");
		for(Map<String,Object> map:list){
			modelList.add(map.get("code").toString());
		}
	}
	public String uploadPackLingx(PackBean source,String basePath,String secret){
		List<String> fileList=new ArrayList<String>();
		List<String> modelList=new ArrayList<String>();
		PackBean packBean=new PackBean();
		packBean.setAppid("335ec1fc-1011-11e5-b7ab-74d02b6b5f61");
		packBean.setAuthor("WWW.LINGX.COM");
		packBean.setContent(source.getContent());
		addFileListByLingx(new File(basePath+"lingx/"),fileList,basePath);
		fileList.addAll(source.getFileList());
		fileList.add("js/ext-theme-classic/ext-theme-classic-all.css");
		fileList.add("WEB-INF/lib/lingx-core-0.0.1-SNAPSHOT.jar");
		fileList.add("WEB-INF/classes/config/page.properties");
		fileList.add("WEB-INF/classes/xml/lingx-action.xml");
		fileList.add("WEB-INF/classes/xml/lingx-default-method.xml");
		packBean.setFileList(fileList);
		addEntityListByLingx(modelList);
		modelList.addAll(source.getModelList());
		packBean.setModelList(modelList);
		packBean.setName(source.getName());
		packBean.setOption(true);
		packBean.setReboot(true);
		packBean.setSecret(secret);
		packBean.setSql(source.getSql());
		packBean.setType(1);
		packBean.setFuncJson(getAllFuncJSON());
		return this.packAndUpload(packBean, basePath, secret);
		
	}
	
	public String getAllFuncJSON(){
		
		return JSON.toJSONString(this.jdbcTemplate.queryForList("select * from tlingx_func "));
	}
	
	public String packAndUpload(PackBean bean,String basePath,String secret){
		try {
			String file=this.pack(bean, basePath);
			//this.upload(new URL(this.uploadURL), new File(file),secret);
			FileInputStream fis=new FileInputStream(new File(file)); 
			String ret=stream(fis,System.currentTimeMillis()+".zip",this.uploadURL,secret,bean);
			System.out.println(ret);
			return ret;
		} catch (Exception e) {
			e.printStackTrace();
		} 
		return "{}";
	}
	public static String stream(InputStream input,String filename,String httpUrl,String secret,PackBean bean){
		StringBuilder sb=new StringBuilder();
		try{
			URL url = new URL(httpUrl);  
	        HttpURLConnection conn = (HttpURLConnection) url.openConnection();  
	        conn.setConnectTimeout(25000);
			conn.setReadTimeout(25000);
			HttpURLConnection.setFollowRedirects(true);
			// 请求方式
			conn.setRequestMethod("POST");
	        conn.setRequestProperty("Connection", "Keep-Alive");  
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("filename",filename);
			conn.setRequestProperty("Content-Length",String.valueOf(input.available()));
			conn.setRequestProperty("Content-Type", "application/octet-stream");
			conn.setRequestProperty("Lingx-Secret",secret);
			conn.setRequestProperty("Lingx-Content", URLEncoder.encode(bean.getContent(), "UTF-8"));
			OutputStream out=conn.getOutputStream();
	        byte[] buff=new byte[4096];
	        int len=-1;
	        while((len=input.read(buff))!=-1){
	        	out.write(buff,0,len);
	        }
	        
	        out.flush();
	        out.close();
	        input.close();
	        InputStream in=conn.getInputStream();
	        while((len=in.read(buff))!=-1){//System.out.println(len);
	        	sb.append(new String(buff,0,len));
	        }
	        in.close();
	        conn.disconnect();
		}catch(Exception e){
			e.printStackTrace();
		}
		return sb.toString();
	}
	
	private String zip(File folder, File zipFile) throws Exception{
		ZipUtils.compressDirectory2Zip(folder.getAbsolutePath(), zipFile.getAbsolutePath());
		return zipFile.getAbsolutePath();
	}
	private String getOptionJSON(JdbcTemplate jdbc,String appid){
		List<OptionBean> options=new ArrayList<OptionBean>();
		List<Map<String,Object>> list=jdbc.queryForList("select * from tlingx_option where app_id=?",appid);
		for(Map<String,Object> map:list){
			OptionBean bean=new OptionBean();
			bean.setName(map.get("name").toString());
			bean.setCode(map.get("code").toString());
			bean.setAppid(map.get("app_id").toString());

			List<Map<String,Object>> list2=jdbc.queryForList("select * from tlingx_optionitem where option_id=?",map.get("id"));
			List<ItemBean> items=new ArrayList<ItemBean>();
			for(Map<String,Object> m:list2){
				ItemBean item=new ItemBean();
				item.setName(m.get("name").toString());
				item.setValue(m.get("value")!=null?m.get("value").toString():"");
				item.setOrderindex(m.get("orderindex").toString());
				item.setEnabled(m.get("enabled").toString());
				items.add(item);
			}
			bean.setItems(items);
			options.add(bean);
		}
		return JSON.toJSONString(options);
	}
	private void init(Map<String,Object> config,PackBean bean){
		//config.put("secret",bean.getSecret());
		config.put("name", bean.getName());
		config.put("content", bean.getContent());
		config.put("author", bean.getAuthor());
		config.put("time", Utils.getTime());
		config.put("appid", bean.getAppid());
		config.put("type", bean.getType());
		config.put("isReboot", bean.isReboot());
		config.put("sql", bean.getSql());
		
	}

	public String getBasePath() {
		return basePath;
	}

	public String getUploadURL() {
		return uploadURL;
	}

	public String getZipPath() {
		return zipPath;
	}

	public String getZipFile() {
		return zipFile;
	}

	public JdbcTemplate getJdbcTemplate() {
		return jdbcTemplate;
	}

	public IModelService getModelService() {
		return modelService;
	}
}
